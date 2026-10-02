package com.kant.llm.eval.agent.production.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.agent.service.budget.TokenEstimator;
import com.kant.agent.service.production.SampleGenerationWorkflow;
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

/**
 * 独立Ollama生成角色，默认关闭。无工具、检索、记忆、反思Advisor或请求正文日志。
 * 一次操作只进行一次显式call，不因解析失败或模型拒绝自动补发。
 */
@Component
public class OllamaSampleGenerationAdapter implements SampleGenerationPort {
    private final boolean enabled;
    private final String baseUrl;
    private final String modelName;
    private final int outputLimit;
    private final double temperature;
    private final int requestTimeoutSeconds;
    private final ObjectMapper json;
    private final Profile profile;

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
        String fingerprint = support.hash(Map.of("baseUrl", baseUrl, "model", modelName, "outputLimit", outputLimit, "temperature", temperature,
                "requestTimeoutSeconds", requestTimeoutSeconds, "promptVersion", SampleGenerationWorkflow.VERSION, "adapterVersion", "ollama-generator-v1"));
        this.profile = new Profile("genprofile_" + fingerprint, enabled && !modelName.isBlank(), modelName, outputLimit);
    }

    @Override
    public Profile profile() {
        return profile;
    }

    @Override
    public Result generate(Command command) {
        if (!enabled || !profile.available() || !profile.profileRef().equals(command.profileRef())
                || !SampleGenerationWorkflow.SYSTEM_PROMPT.equals(command.systemPrompt()))
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
        long input = TokenEstimator.estimate(command.systemPrompt()) + TokenEstimator.estimate(command.userPayloadJson());
        long output = TokenEstimator.estimate(raw);
        String measurement = "ESTIMATED";
        var usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage != null && usage.getPromptTokens() != null && usage.getCompletionTokens() != null && usage.getPromptTokens() > 0 && usage.getCompletionTokens() >= 0) {
            input = usage.getPromptTokens();
            output = usage.getCompletionTokens();
            measurement = "ACTUAL";
        }
        // 非法结构仍按已收到响应结算，只保存分类和计量，不保存可能包含内部思维链的原始响应。
        try {
            if (raw.length() > 100000) return new Result("INVALID", null, "结构化响应过大", input, output, measurement);
            JsonNode tree = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(raw);
            if (tree == null || !tree.isObject())
                return new Result("INVALID", null, "未返回单一JSON对象", input, output, measurement);
            String status = tree.path("status").asText();
            if ("REFUSED".equals(status))
                return new Result("REFUSED", null, "生成模型拒绝该请求", input, output, measurement);
            var content = tree.path("content");
            if (!"GENERATED".equals(status) || !content.isTextual() || content.asText().length() > 10000)
                return new Result("INVALID", null, "状态、正文类型或长度不满足契约", input, output, measurement);
            return new Result("GENERATED", content.asText(), "", input, output, measurement);
        } catch (java.io.IOException ex) {
            return new Result("INVALID", null, "结构化响应解析失败", input, output, measurement);
        }
    }
}
