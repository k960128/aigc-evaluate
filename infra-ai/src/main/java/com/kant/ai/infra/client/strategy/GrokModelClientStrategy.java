package com.kant.ai.infra.client.strategy;

import cn.hutool.core.util.ObjectUtil;
import com.kant.ai.infra.client.*;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;

@Slf4j
public class GrokModelClientStrategy implements ModelClientStrategy {

    private static final String CONNECTION_TEST_PROMPT = "只返回1，不要返回其他任何内容！";
    private final ObservationRegistry observationRegistry;

    public GrokModelClientStrategy(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public ModelResponse call(ModelRequest modelRequest) {
        long startTime = System.currentTimeMillis();
        ChatClient.CallResponseSpec response = ChatClientRequestSupport
                .prompt(createChatClient(modelRequest), modelRequest)
                .call();
        String content = response.content();
        long endTime = System.currentTimeMillis();
        return ModelResponse.builder()
                .modelId(modelRequest.getClientModelInfo().getModelId())
                .respContent(content)
                .elapsed(endTime - startTime)
                .build();
    }

    @Override
    public ModelManufacturerEnum getManufacturer() {
        return ModelManufacturerEnum.GROK;
    }

    @Override
    public ModelConnectionResponse connection(ModelRequest modelRequest) {
        long startTime = System.currentTimeMillis();
        String content = createChatClient(modelRequest)
                .prompt(CONNECTION_TEST_PROMPT)
                .call()
                .content();
        long endTime = System.currentTimeMillis();
        return ModelConnectionResponse.builder()
                .result(format(content))
                .respContent(content)
                .elapsed(endTime - startTime)
                .build();
    }

    private ChatClient createChatClient(ModelRequest modelRequest) {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(modelRequest.getClientModelInfo().getBaseUrl())
                        .apiKey(modelRequest.getClientModelInfo().getApiKey())
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelRequest.getClientModelInfo().getModel())
                        .build())
                .observationRegistry(observationRegistry)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
        return ChatClient.create(chatModel, observationRegistry);
    }

    /**
     * 格式化
     */
    private Boolean format(String content) {
        // 剔除内容中包含的\n
        content = content.replaceAll("\\n", "");
        return ObjectUtil.equal(content, "1");
    }
}
