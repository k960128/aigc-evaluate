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
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.retry.RetryUtils;

@Slf4j
public class GlmModelClientStrategy implements ModelClientStrategy {
    private final ObservationRegistry observationRegistry;

    public GlmModelClientStrategy(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }


    @Override
    public ModelResponse call(ModelRequest modelRequest) {
        long startTime = System.currentTimeMillis();
        ChatModel chatModel = new ZhiPuAiChatModel(ZhiPuAiApi.builder()
                .apiKey(modelRequest.getClientModelInfo().getApiKey())
                .build(),
                ZhiPuAiChatOptions.builder()
                        .model(modelRequest.getClientModelInfo().getModel())
                        .build(), RetryUtils.DEFAULT_RETRY_TEMPLATE, observationRegistry);
        ChatClient client = ChatClient.create(chatModel, observationRegistry);
        ChatClient.ChatClientRequestSpec requestSpec = client.prompt(modelRequest.getInputText());
        ChatClient.CallResponseSpec call = requestSpec.call();
        long endTime = System.currentTimeMillis();
        return ModelResponse.builder()
                .modelId(modelRequest.getClientModelInfo().getModelId())
                .respContent(call.content())
                .elapsed(endTime - startTime)
                .build();
    }


    @Override
    public ModelManufacturerEnum getManufacturer() {
        return ModelManufacturerEnum.GLM;
    }

    @Override
    public ModelConnectionResponse connection(ModelRequest modelRequest) {
        long startTime = System.currentTimeMillis();
        ChatModel chatModel = new ZhiPuAiChatModel(ZhiPuAiApi.builder()
                .apiKey(modelRequest.getClientModelInfo().getApiKey())
                .baseUrl(modelRequest.getClientModelInfo().getBaseUrl())
                .build(),
                ZhiPuAiChatOptions.builder()
                        .model(modelRequest.getClientModelInfo().getModel())
                        .build(), RetryUtils.DEFAULT_RETRY_TEMPLATE, observationRegistry);

        ChatClient client = ChatClient.create(chatModel, observationRegistry);
        ChatClient.ChatClientRequestSpec requestSpec = client.prompt("只返回1,不要返回其他任何内容!");
        ChatClient.CallResponseSpec call = requestSpec.call();
        long endTime = System.currentTimeMillis();
        return ModelConnectionResponse.builder()
                .result(ObjectUtil.equal(call.content(), "1"))
                .respContent(call.content())
                .elapsed(endTime - startTime)
                .build();
    }
}
