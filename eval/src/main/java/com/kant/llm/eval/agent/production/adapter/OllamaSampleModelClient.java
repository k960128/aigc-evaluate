package com.kant.llm.eval.agent.production.adapter;

import com.kant.agent.port.production.SampleGenerationPort;
import com.kant.agent.service.budget.TokenEstimator;
import com.kant.llm.eval.agent.production.config.SampleFactoryModelConfiguration;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 两个角色共用的无状态单次传输实现，不共用模型配置、会话或评审逻辑。
 * 无工具、隐藏Advisor、正文日志和内部重试；预算和状态仍由外层按每笔物理调用记录。
 */
@Component
public class OllamaSampleModelClient {
    /**
     * 使用指定角色配置发送一次模型请求，严格核对命令中的角色指纹和固定提示词。
     * @param role 当前阶段的不可变部署配置，包含该角色自己的模型、输出限制和超时
     * @param command 已持久化的命令；profileRef是单角色身份，不是计划组合身份
     * @param expectedPrompt 调用适配器固定的Actor或Critic平台提示词
     * @return 仅供内存解析的原始文本和本次用量，不能直接作为API或数据库结果
     */
    public Response callOnce(SampleFactoryModelConfiguration.Role role, SampleGenerationPort.Command command,
                             String expectedPrompt) {
        var profile = role.profile();
        if (!profile.available() || !profile.profileRef().equals(command.profileRef())
                || !expectedPrompt.equals(command.systemPrompt()))
            throw new IllegalStateException("模型角色未启用或命令快照已变化");
        // 客户端超时只约束等待，不能据此认为供应商没有执行。
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(role.requestTimeoutSeconds()));
        var model = OllamaChatModel.builder().ollamaApi(OllamaApi.builder().baseUrl(role.baseUrl())
                        .restClientBuilder(RestClient.builder().requestFactory(requestFactory)).build())
                .defaultOptions(OllamaChatOptions.builder().model(profile.modelName()).temperature(role.temperature())
                        .numPredict(profile.maxOutputTokens()).disableThinking().build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .observationRegistry(ObservationRegistry.NOOP).build();
        var response = ChatClient.create(model, ObservationRegistry.NOOP).prompt().system(command.systemPrompt())
                .user(command.userPayloadJson()).call().chatResponse();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null)
            throw new IllegalStateException("模型请求未获得可确认响应");
        String raw = response.getResult().getOutput().getText();
        if (raw == null) throw new IllegalStateException("模型响应正文缺失");
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
        return new Response(raw, input, output, measurement);
    }

    /**
     * 原始响应仅短期保存在内存，解析失败时不落库、不写日志。
     * @param text 本次模型返回文本，可能为非法结构或包含私有资料
     * @param inputTokens 本次输入Token
     * @param outputTokens 本次输出Token
     * @param measurement ACTUAL实报或ESTIMATED估算
     */
    public record Response(String text, long inputTokens, long outputTokens, String measurement) { }
}
