package com.kant.llm.eval.agent.production.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.agent.port.production.SampleReflectionPort;
import com.kant.agent.service.budget.TokenEstimator;
import com.kant.agent.service.production.SampleGenerationWorkflow;
import com.kant.agent.service.production.SampleReflectionAgent;
import com.kant.llm.eval.agent.production.support.FactoryJsonSupport;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reflection的Actor/Critic适配器，默认关闭；当前两种角色共用同一模型配置，但分别请求、分别计量。
 * 无工具、检索、记忆、隐藏反思Advisor或正文日志；一次操作只进行一次显式call。
 * 同模型评审不是独立安全验证，不因解析失败、评审未通过或模型拒绝在适配器中补发。
 */
@Component
public class OllamaSampleGenerationAdapter implements SampleGenerationPort, SampleReflectionPort {
    /** 显式生成开关，仅配置启用不意味着模型服务可达。 */
    private final boolean enabled;
    /** 独立生成角色的Ollama地址，不是被测目标地址。 */
    private final String baseUrl;
    /** 模型标签；首版不校验标签背后的权重是否被替换。 */
    private final String modelName;
    /** 单次最大输出Token，也是调用前预算预留的一部分。 */
    private final int outputLimit;
    /** 采样温度，属于冻结配置指纹的一部分。 */
    private final double temperature;
    /** 客户端读取超时秒数，不是供应商执行期限或批次整体期限。 */
    private final int requestTimeoutSeconds;
    /** 结构化响应解析器，不用于向日志输出原始响应。 */
    private final ObjectMapper json;
    /** 构造时冻结的角色快照，同一Bean生命周期内不随查询重新计算。 */
    private final Profile profile;

    /**
     * 校验部署参数并建立稳定配置指纹，不发起模型请求。
     * TreeMap按键排序，真实配置值或版本变化仍应改变profileRef。
     * enabled只控制可用性，不进入指纹，重新开启相同配置不等于更换生成角色。
     */
    public OllamaSampleGenerationAdapter(@Value("${eval.agent.sample-factory.generator.enabled:false}") boolean enabled,
                                         @Value("${eval.agent.sample-factory.generator.base-url:http://localhost:11434}") String baseUrl,
                                         @Value("${eval.agent.sample-factory.generator.model:}") String modelName,
                                         @Value("${eval.agent.sample-factory.generator.max-output-tokens:1024}") int outputLimit,
                                         @Value("${eval.agent.sample-factory.generator.temperature:0.7}") double temperature,
                                         @Value("${eval.agent.sample-factory.generator.request-timeout-seconds:120}") int requestTimeoutSeconds,
                                         ObjectMapper json, FactoryJsonSupport support) {
        if (outputLimit < 64 || outputLimit > 4096 || !Double.isFinite(temperature) || temperature < 0 || temperature > 2)
            throw new IllegalArgumentException("生成角色输出或温度配置不合法");
        if (requestTimeoutSeconds < 10 || requestTimeoutSeconds > 600)
            throw new IllegalArgumentException("生成角色请求超时为10至600秒");
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.outputLimit = outputLimit;
        this.temperature = temperature;
        this.json = json;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        // 使用固定顺序的TreeMap生成配置快照，避免Map.of的无序遍历导致应用重启后profileRef变化。
        // profileRef属于生产计划的配置身份；同一组配置必须跨进程、跨重启得到同一个指纹。
        Map<String, Object> fingerprintSource = new TreeMap<>();
        fingerprintSource.put("adapterVersion", "ollama-reflection-v1");
        fingerprintSource.put("agentVersion", SampleReflectionAgent.VERSION);
        fingerprintSource.put("criticVersion", SampleReflectionAgent.CRITIC_VERSION);
        fingerprintSource.put("qualityVersion", SampleReflectionAgent.QUALITY_VERSION);
        fingerprintSource.put("baseUrl", baseUrl);
        fingerprintSource.put("model", modelName);
        fingerprintSource.put("outputLimit", outputLimit);
        fingerprintSource.put("promptVersion", SampleGenerationWorkflow.VERSION);
        fingerprintSource.put("requestTimeoutSeconds", requestTimeoutSeconds);
        fingerprintSource.put("temperature", temperature);
        String fingerprint = support.hash(fingerprintSource);
        this.profile = new Profile("genprofile_" + fingerprint, enabled && !modelName.isBlank(), modelName, outputLimit);
    }

    /** 返回已冻结配置，不执行网络探活或生成。 */
    @Override
    public Profile profile() {
        return profile;
    }

    /**
     * 对固定命令执行一次同步模型调用，由外层Worker承担异步受理、预算和恢复。
     * @param command 必须匹配本实例配置及平台系统提示词的固定输入
     * @return 生成、拒绝或非法结构的分类和Token用量；非法结构仍是已确认响应
     * @throws IllegalStateException 配置不匹配或没有可确认响应，外层保守记录未知操作
     */
    @Override
    public SampleGenerationPort.Result generate(Command command) {
        var response = callOnce(command, SampleGenerationWorkflow.SYSTEM_PROMPT);
        String raw = response.text();
        long input = response.inputTokens();
        long output = response.outputTokens();
        String measurement = response.measurement();
        // 非法结构仍按已收到响应结算，只保存分类和计量，不保存原始响应。
        try {
            if (raw.length() > 100000) return new SampleGenerationPort.Result("INVALID", null, "结构化响应过大", input, output, measurement);
            JsonNode tree = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(raw);
            if (tree == null || !tree.isObject())
                return new SampleGenerationPort.Result("INVALID", null, "未返回单一JSON对象", input, output, measurement);
            String status = tree.path("status").asText();
            if ("REFUSED".equals(status))
                return new SampleGenerationPort.Result("REFUSED", null, "生成模型拒绝该请求", input, output, measurement);
            var content = tree.path("content");
            if (!"GENERATED".equals(status) || !content.isTextual() || content.asText().length() > 10000)
                return new SampleGenerationPort.Result("INVALID", null, "状态、正文类型或长度不满足契约", input, output, measurement);
            return new SampleGenerationPort.Result("GENERATED", content.asText(), "", input, output, measurement);
        } catch (java.io.IOException ex) {
            return new SampleGenerationPort.Result("INVALID", null, "结构化响应解析失败", input, output, measurement);
        }
    }

    /** 一次独立Critic请求；严格解析结果，不修补JSON，不在失败时调用第二次模型。 */
    @Override
    public SampleReflectionPort.Result reflect(Command command) {
        var response = callOnce(command, SampleReflectionAgent.CRITIC_PROMPT);
        try {
            if (response.text().length() > 100000) return invalidReflection(response);
            JsonNode tree = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(response.text());
            if (tree == null || !tree.isObject() || !tree.path("outcome").isTextual() || !tree.path("issues").isArray())
                return invalidReflection(response);
            String outcome = tree.path("outcome").asText();
            var issuesNode = tree.path("issues");
            if (!Set.of("PASS", "REVISE", "REJECT").contains(outcome) || issuesNode.size() > 5)
                return invalidReflection(response);
            List<String> issues = new ArrayList<>();
            for (JsonNode item : issuesNode) {
                if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 500
                        || item.asText().codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
                    return invalidReflection(response);
                issues.add(item.asText());
            }
            if ("PASS".equals(outcome) != issues.isEmpty()) return invalidReflection(response);
            return new SampleReflectionPort.Result(outcome, issues, response.inputTokens(), response.outputTokens(), response.measurement());
        } catch (java.io.IOException ex) {
            return invalidReflection(response);
        }
    }

    /** 非法评审也是已确认响应，保留消耗并保守拒绝，不能伪装成通过或零消耗。 */
    private SampleReflectionPort.Result invalidReflection(ModelResponse response) {
        return new SampleReflectionPort.Result("INVALID", List.of("模型评审未返回合法的结构化质量结论"),
                response.inputTokens(), response.outputTokens(), response.measurement());
    }

    /** 共享单次网络调用。原始文本仅在内存解析，不写入普通日志、观察器或数据库。 */
    private ModelResponse callOnce(Command command, String expectedPrompt) {
        if (!enabled || !profile.available() || !profile.profileRef().equals(command.profileRef())
                || !expectedPrompt.equals(command.systemPrompt()))
            throw new IllegalStateException("生成角色未启用或快照已变化");
        // 有限连接/读取超时只限制客户端等待，不等于供应商一定没有执行。
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(requestTimeoutSeconds));
        var model = OllamaChatModel.builder().ollamaApi(OllamaApi.builder().baseUrl(baseUrl)
                        .restClientBuilder(RestClient.builder().requestFactory(requestFactory)).build())
                .defaultOptions(OllamaChatOptions.builder().model(modelName).temperature(temperature).numPredict(outputLimit).disableThinking().build())
                // 禁止Spring AI内部RetryTemplate再次发送；超时的物理操作必须留UNKNOWN由事实恢复。
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .observationRegistry(ObservationRegistry.NOOP).build();
        var response = ChatClient.create(model, ObservationRegistry.NOOP).prompt().system(command.systemPrompt()).user(command.userPayloadJson()).call().chatResponse();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null)
            throw new IllegalStateException("生成请求未获得可确认响应");
        String raw = response.getResult().getOutput().getText();
        if (raw == null) throw new IllegalStateException("生成响应正文缺失");
        // 优先采用完整供应商usage；缺失时字符估算并标记ESTIMATED，不能冒充精确计费值。
        long input = TokenEstimator.estimate(command.systemPrompt()) + TokenEstimator.estimate(command.userPayloadJson());
        long output = TokenEstimator.estimate(raw);
        String measurement = "ESTIMATED";
        var usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage != null && usage.getPromptTokens() != null && usage.getCompletionTokens() != null && usage.getPromptTokens() > 0 && usage.getCompletionTokens() >= 0) {
            input = usage.getPromptTokens();
            output = usage.getCompletionTokens();
            measurement = "ACTUAL";
        }
        return new ModelResponse(raw, input, output, measurement);
    }

    /** 单次网络响应的短期内存容器，不作为持久化契约或API响应。 */
    private record ModelResponse(String text, long inputTokens, long outputTokens, String measurement) { }
}
