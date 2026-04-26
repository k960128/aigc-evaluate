package com.kant.llm.eval.client.strategy;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.kant.llm.eval.client.ModelClientStrategy;
import com.kant.llm.eval.client.ModelRequest;
import com.kant.llm.eval.client.ModelResponse;
import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

@Slf4j
public class QwenModelClientStrategy implements ModelClientStrategy {

    @Override
    public ModelResponse call(ModelRequest modelRequest) {
        long startTime = System.currentTimeMillis();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(modelRequest.getModelInfo().getBaseUrl())
                        .apiKey(modelRequest.getModelInfo().getApiKey())
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelRequest.getModelInfo().getModel())
                        .build())
                .build();

        ChatClient client = ChatClient.builder(chatModel).build();
        ChatClient.ChatClientRequestSpec requestSpec = client.prompt(modelRequest.getInputText());
        log.info("QwenModelClientStrategy requestSpec: {}", requestSpec);
        ChatClient.CallResponseSpec call = requestSpec.call();
        log.info("QwenModelClientStrategy call: {}", call);
        long endTime = System.currentTimeMillis();
        return ModelResponse.builder()
                .modelId(modelRequest.getModelInfo().getModelId())
                .respContent(call.content())
                .elapsed(endTime - startTime)
                .build();
    }


    @Override
    public ModelManufacturerEnum getManufacturer() {
        return ModelManufacturerEnum.QWEN;
    }
}
