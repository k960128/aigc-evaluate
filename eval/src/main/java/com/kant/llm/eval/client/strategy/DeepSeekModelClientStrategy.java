package com.kant.llm.eval.client.strategy;

import com.kant.llm.eval.client.ModelClientStrategy;
import com.kant.llm.eval.client.ModelRequest;
import com.kant.llm.eval.client.ModelResponse;
import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

@Slf4j
public class DeepSeekModelClientStrategy implements ModelClientStrategy {

    @Override
    public ModelResponse call(ModelRequest modelRequest) {
        long startTime = System.currentTimeMillis();
        ChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(DeepSeekApi.builder()
                        .apiKey(modelRequest.getModelInfo().getApiKey())
                        .baseUrl(modelRequest.getModelInfo().getBaseUrl())
                        .build())
                .defaultOptions(DeepSeekChatOptions.builder()
                        .model(modelRequest.getModelInfo().getModel())
                        .build())
                .build();
        ChatClient client = ChatClient.builder(chatModel).build();
        ChatClient.ChatClientRequestSpec requestSpec = client.prompt(modelRequest.getInputText());
        log.error("DeepSeekModelClientStrategy requestSpec: {}", requestSpec);
        ChatClient.CallResponseSpec call = requestSpec.call();
        log.error("DeepSeekModelClientStrategy call: {}", call);
        long endTime = System.currentTimeMillis();
        return ModelResponse.builder()
                .modelId(modelRequest.getModelInfo().getModelId())
                .respContent(call.content())
                .elapsed(endTime - startTime)
                .build();
    }

    @Override
    public ModelManufacturerEnum getManufacturer() {
        return ModelManufacturerEnum.DEEPSEEK;
    }
}
