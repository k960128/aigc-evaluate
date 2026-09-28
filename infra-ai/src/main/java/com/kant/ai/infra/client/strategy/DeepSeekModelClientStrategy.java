package com.kant.ai.infra.client.strategy;

import cn.hutool.core.util.ObjectUtil;
import com.kant.ai.infra.client.*;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DeepSeekModelClientStrategy implements ModelClientStrategy {
    private final ObservationRegistry observationRegistry;

    public DeepSeekModelClientStrategy(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }


    @Override
    public ModelResponse call(ModelRequest modelRequest) {
        long startTime = System.currentTimeMillis();
        ChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(DeepSeekApi.builder()
                        .apiKey(modelRequest.getClientModelInfo().getApiKey())
                        .baseUrl(modelRequest.getClientModelInfo().getBaseUrl())
                        .build())
                .defaultOptions(DeepSeekChatOptions.builder()
                        .model(modelRequest.getClientModelInfo().getModel())
                        .build())
                .observationRegistry(observationRegistry)
                .build();
        ChatClient client = ChatClient.create(chatModel, observationRegistry);
        ChatClient.ChatClientRequestSpec requestSpec = client.prompt(modelRequest.getInputText());
        log.error("DeepSeekModelClientStrategy requestSpec: {}", requestSpec);
        ChatClient.CallResponseSpec call = requestSpec.call();
        log.error("DeepSeekModelClientStrategy call: {}", call);
        long endTime = System.currentTimeMillis();
        return ModelResponse.builder()
                .modelId(modelRequest.getClientModelInfo().getModelId())
                .respContent(call.content())
                .elapsed(endTime - startTime)
                .build();

    }

    @Override
    public ModelManufacturerEnum getManufacturer() {
        return ModelManufacturerEnum.DEEPSEEK;
    }

    @Override
    public ModelConnectionResponse connection(ModelRequest modelRequest) {
        long startTime = System.currentTimeMillis();
        ChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(DeepSeekApi.builder()
                        .apiKey(modelRequest.getClientModelInfo().getApiKey())
                        .baseUrl(modelRequest.getClientModelInfo().getBaseUrl())
                        .build())
                .defaultOptions(DeepSeekChatOptions.builder()
                        .model(modelRequest.getClientModelInfo().getModel())
                        .build())
                .observationRegistry(observationRegistry)
                .build();
        ChatClient client = ChatClient.create(chatModel, observationRegistry);
        ChatClient.ChatClientRequestSpec requestSpec = client.prompt("只返回1,不要返回其他任何内容!");
        ChatClient.CallResponseSpec call = requestSpec.call();
        String content = call.content();
        long endTime = System.currentTimeMillis();
        return ModelConnectionResponse.builder()
                .result(ObjectUtil.equal(content, "1"))
                .respContent(content)
                .elapsed(endTime - startTime)
                .build();
    }
}
