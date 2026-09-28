package com.kant.ai.infra.client.strategy;

import cn.hutool.core.util.ObjectUtil;
import com.kant.ai.infra.client.ModelClientStrategy;
import com.kant.ai.infra.client.ModelConnectionResponse;
import com.kant.ai.infra.client.ModelRequest;
import com.kant.ai.infra.client.ModelResponse;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

@Slf4j
public class QwenModelClientStrategy implements ModelClientStrategy {
    private final ObservationRegistry observationRegistry;

    public QwenModelClientStrategy(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }


    @Override
    public ModelResponse call(ModelRequest modelRequest) {
        long startTime = System.currentTimeMillis();
        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(modelRequest.getClientModelInfo().getBaseUrl())
                        .apiKey(modelRequest.getClientModelInfo().getApiKey())
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelRequest.getClientModelInfo().getModel())
                        .build())
                .observationRegistry(observationRegistry)
                .build();

        ChatClient client = ChatClient.create(chatModel, observationRegistry);
        ChatClient.ChatClientRequestSpec requestSpec = client.prompt(modelRequest.getInputText());
        log.info("QwenModelClientStrategy requestSpec: {}", requestSpec);
        ChatClient.CallResponseSpec call = requestSpec.call();
        log.info("QwenModelClientStrategy call: {}", call);
        long endTime = System.currentTimeMillis();
        return ModelResponse.builder()
                .modelId(modelRequest.getClientModelInfo().getModelId())
                .respContent(call.content())
                .elapsed(endTime - startTime)
                .build();
    }


    @Override
    public ModelManufacturerEnum getManufacturer() {
        return ModelManufacturerEnum.QWEN;
    }

    @Override
    public ModelConnectionResponse connection(ModelRequest modelRequest) {
        long startTime = System.currentTimeMillis();
        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(modelRequest.getClientModelInfo().getBaseUrl())
                        .apiKey(modelRequest.getClientModelInfo().getApiKey())
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelRequest.getClientModelInfo().getModel())
                        .build())
                .observationRegistry(observationRegistry)
                .build();

        ChatClient client = ChatClient.create(chatModel, observationRegistry);
        ChatClient.ChatClientRequestSpec requestSpec = client.prompt("只返回1,不要返回其他任何内容!");
        log.info("QwenModelClientStrategy requestSpec: {}", requestSpec);
        ChatClient.CallResponseSpec call = requestSpec.call();
        log.info("QwenModelClientStrategy call: {}", call);
        long endTime = System.currentTimeMillis();
        return ModelConnectionResponse.builder()
                .result(ObjectUtil.equal(call.content(), "1"))
                .respContent(call.content())
                .elapsed(endTime - startTime)
                .build();
    }
}
