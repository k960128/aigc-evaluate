package com.kant.springai.agent.common.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * 为销售多 Agent Demo 延迟创建底层模型。
 *
 * <p>延迟创建可以保证没有配置 Demo API Key 时，应用仍然能够正常启动；
 * 只有实际调用 Demo 接口时才会校验配置。</p>
 */
@Component
public class SalesMultiAgentModelProvider {

    private final SalesMultiAgentProperties properties;
    private final ObservationRegistry observationRegistry;

    private volatile ChatModel chatModel;

    public SalesMultiAgentModelProvider(SalesMultiAgentProperties properties,
                                        ObservationRegistry observationRegistry) {
        this.properties = properties;
        this.observationRegistry = observationRegistry;
    }

    public ChatModel getChatModel() {
        ChatModel current = chatModel;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (chatModel == null) {
                chatModel = createChatModel();
            }
            return chatModel;
        }
    }

    private ChatModel createChatModel() {
        Assert.hasText(properties.getApiKey(),
                "请配置 sales-agent.multi-agent.api-key 后再调用 Supervisor Demo");
        Assert.hasText(properties.getBaseUrl(),
                "sales-agent.multi-agent.base-url 不能为空");
        Assert.hasText(properties.getModel(),
                "sales-agent.multi-agent.model 不能为空");

        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey(properties.getApiKey())
                        .baseUrl(properties.getBaseUrl())
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.getModel())
                        .build())
                .observationRegistry(observationRegistry)
                .build();
    }
}
