package com.kant.llm.eval.agent.production.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.agent.service.budget.TokenEstimator;
import com.kant.agent.service.production.SampleReflectionAgent;
import com.kant.llm.eval.agent.production.config.SampleFactoryModelConfiguration;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 两个角色共用的无状态单次传输实现，不共用模型配置、会话或评审逻辑。
 * 无工具、隐藏Advisor和内部重试；预算和状态仍由外层按每笔物理调用记录。
 * 响应元数据使用INFO，正常原文使用DEBUG，结构非法的有界原文使用WARN，便于关联单次调用排查。
 */
@Slf4j
@Component
public class OllamaSampleModelClient {
    /** 日志正文的最大UTF-16单元数；只截断日志副本，不影响实际解析、候选内容或Token结算。 */
    private static final int MAX_LOG_RESPONSE_CHARACTERS = 16384;
    /** 将响应日志编码为单行JSON字符串，保留换行等字符的可见表示，不让模型文本伪造多行日志。 */
    private final ObjectMapper json;

    /** 只注入现有JSON组件，用于日志转义，不访问模型或变更模型配置。 */
    public OllamaSampleModelClient(ObjectMapper json) {
        this.json = json;
    }

    /**
     * 这是D05唯一的模型网络传输实现。
     * 通过“单次请求、无工具、无隐藏重试、无对话记忆”的约束，让每一笔物理调用都能对应数据库操作记录。
     */
    /**
     * 使用指定角色配置发送一次模型请求，严格核对命令中的角色指纹和固定提示词。
     * @param role 当前阶段的不可变部署配置，包含该角色自己的模型、输出限制和超时
     * @param command 已持久化的命令；profileRef是单角色身份，不是计划组合身份
     * @param expectedPrompt 调用适配器固定的Actor或Critic平台提示词
     * @param phase 本次生成或评审阶段，用于日志关联，不改变模型参数或状态推进
     * @return 仅供内存解析的原始文本和本次用量，不能直接作为API或数据库结果
     */
    public Response callOnce(SampleFactoryModelConfiguration.Role role, SampleGenerationPort.Command command,
                             String expectedPrompt, SampleReflectionAgent.Phase phase) {
        // 调用前校验角色指纹和系统提示词，防止旧检查点被当前错误角色或错误提示词执行。
        var profile = role.profile();
        if (!profile.available() || !profile.profileRef().equals(command.profileRef())
                || !expectedPrompt.equals(command.systemPrompt()))
            throw new IllegalStateException("模型角色未启用或命令快照已变化");
        // 客户端超时只约束等待，不能据此认为供应商没有执行；外层会把未确认结果视为UNKNOWN。
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(role.requestTimeoutSeconds()));
        var model = OllamaChatModel.builder().ollamaApi(OllamaApi.builder().baseUrl(role.baseUrl())
                        .restClientBuilder(RestClient.builder().requestFactory(requestFactory)).build())
                .defaultOptions(OllamaChatOptions.builder().model(profile.modelName()).temperature(role.temperature())
                        .numPredict(profile.maxOutputTokens()).disableThinking().build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .observationRegistry(ObservationRegistry.NOOP).build();
        var response = ChatClient
                .create(model, ObservationRegistry.NOOP)
                .prompt()
                .advisors(new SimpleLoggerAdvisor())
                .system(command.systemPrompt())
                .user(command.userPayloadJson()).call().chatResponse();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            log.warn("D05模型响应缺失，operationId={}, phase={}, model={}", command.operationId(), phase, profile.modelName());
            throw new IllegalStateException("模型请求未获得可确认响应");
        }
        // 原文交给上层适配器严格解析；仅输出有界诊断日志，不将原文落库或加入业务接口响应。
        String raw = response.getResult().getOutput().getText();
        if (raw == null) {
            log.warn("D05模型响应正文缺失，operationId={}, phase={}, model={}", command.operationId(), phase, profile.modelName());
            throw new IllegalStateException("模型响应正文缺失");
        }
        // 供应商未返回完整用量时使用平台估算，并明确标记ESTIMATED，避免把估算冒充计费事实。
        long input = TokenEstimator.estimate(command.systemPrompt()) + TokenEstimator.estimate(command.userPayloadJson());
        long output = TokenEstimator.estimate(raw);
        String measurement = "ESTIMATED";
        var usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage != null && usage.getPromptTokens() != null && usage.getCompletionTokens() != null
                && usage.getPromptTokens() > 0 && usage.getCompletionTokens() >= 0) {
            input = usage.getPromptTokens();
            output = usage.getCompletionTokens();
            measurement = "ACTUAL";
        }
        // finishReason如为length，可与输出Token上限一起核对模型是否被截断；供应商未提供时保持null。
        String finishReason = response.getResult().getMetadata() == null ? null : response.getResult().getMetadata().getFinishReason();
        log.info("D05模型响应已返回，operationId={}, phase={}, model={}, finishReason={}, inputTokens={}, outputTokens={}, maxOutputTokens={}, measurement={}, rawLength={}",
                command.operationId(), phase, profile.modelName(), finishReason, input, output, profile.maxOutputTokens(), measurement, raw.length());
        if (log.isDebugEnabled()) {
            log.debug("D05模型响应原文，operationId={}, phase={}, rawLength={}, truncated={}, rawResponse={}",
                    command.operationId(), phase, raw.length(), raw.length() > MAX_LOG_RESPONSE_CHARACTERS, responsePreview(raw));
        }
        return new Response(raw, input, output, measurement);
    }

    /**
     * 记录已确认但结构非法的响应，默认WARN级别即可查看；不将解析失败改成网络UNKNOWN或触发重试。
     * @param phase GENERATE为Actor生成/改写，REFLECT为Critic评审
     * @param command 原物理调用命令，只记录operationId，不打印种子、系统提示词或反馈
     * @param response 模型实际返回文本和计量，日志截断不修改此对象
     * @param reason 适配器固定的失败分类，不混入模型提供的指令
     * @param error JSON解析异常；字段契约不符时为null，仅记录异常类型及行列，不打印包含正文的异常消息
     */
    public void logInvalidResponse(SampleReflectionAgent.Phase phase, SampleGenerationPort.Command command,
                                   Response response, String reason, IOException error) {
        var location = error instanceof JsonProcessingException parsingError ? parsingError.getLocation() : null;
        log.warn("D05模型响应结构非法，operationId={}, phase={}, reason={}, errorType={}, line={}, column={}, rawLength={}, truncated={}, rawResponse={}",
                command.operationId(), phase, reason, error == null ? null : error.getClass().getSimpleName(),
                location == null ? null : location.getLineNr(), location == null ? null : location.getColumnNr(),
                response.text().length(), response.text().length() > MAX_LOG_RESPONSE_CHARACTERS, responsePreview(response.text()));
    }

    /** 截取日志副本并转义为单行JSON字符串；日志编码失败只返回固定标记，不影响已确认响应的处理。 */
    private String responsePreview(String raw) {
        String preview = raw.substring(0, Math.min(raw.length(), MAX_LOG_RESPONSE_CHARACTERS));
        try {
            return json.writeValueAsString(preview);
        } catch (JsonProcessingException ex) {
            return "[模型响应日志转义失败]";
        }
    }

    /**
     * 原始响应短期保存在内存，不落库；诊断日志仅保留有界副本，不改变返回正文。
     * @param text 本次模型返回文本，可能为非法结构或包含私有资料
     * @param inputTokens 本次输入Token
     * @param outputTokens 本次输出Token
     * @param measurement ACTUAL实报或ESTIMATED估算
     */
    public record Response(String text, long inputTokens, long outputTokens, String measurement) { }
}
