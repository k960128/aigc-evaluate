package com.kant.llm.eval.client.strategy;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.llm.eval.client.*;
import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import com.kant.llm.eval.dao.entity.MockModelOutputContentDO;
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
    public ModelResponse mockCall(MockModelRequest modelRequest) {
        log.info("mock data");
        MockModelOutputContentDO contentDO = modelRequest.getMockModelOutputContentService().list(
                new LambdaQueryWrapper<MockModelOutputContentDO>()
                        .eq(MockModelOutputContentDO::getBindModelId, modelRequest.getModelInfo().getModelId())
                        .eq(MockModelOutputContentDO::getBindSampleId, modelRequest.getSampleId())
                        .eq(MockModelOutputContentDO::getDeleted, false)
        ).getFirst();
        // 生成模拟数据
        long startTime = System.currentTimeMillis();
        // 200 - 20000的随机数
        int random = ThreadLocalRandom.current().nextInt(200, 20001);
        try {
            // 模拟API调用
            TimeUnit.MILLISECONDS.sleep(random);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long endTime = System.currentTimeMillis();
        return ModelResponse.builder()
                .modelId(modelRequest.getModelInfo().getModelId())
                .respContent(ObjectUtil.isNotNull(contentDO) ? contentDO.getOutputText() : "")
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
                        .apiKey(modelRequest.getModelInfo().getApiKey())
                        .baseUrl(modelRequest.getModelInfo().getBaseUrl())
                        .build())
                .defaultOptions(DeepSeekChatOptions.builder()
                        .model(modelRequest.getModelInfo().getModel())
                        .build())
                .build();
        ChatClient client = ChatClient.builder(chatModel).build();
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
