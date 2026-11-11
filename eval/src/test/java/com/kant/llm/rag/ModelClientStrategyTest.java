package com.kant.llm.rag;

import com.kant.ai.infra.client.*;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import com.kant.llm.eval.basesource.dao.entity.ModelInfoDO;
import com.kant.llm.eval.basesource.service.ModelInfoService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Objects;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class ModelClientStrategyTest {

    @Autowired
    private ModelInfoService modelInfoService;
    @Autowired
    private ModelClientStrategyFactory modelClientStrategyFactory;

    @Test
    void testModelCall() {
        ModelInfoDO modelInfoDO = modelInfoService.getById(3L);
        ClientModelInfo clientModelInfo = ClientModelInfo.builder()
                .modelId(modelInfoDO.getId())
                .model(modelInfoDO.getModel())
                .apiKey(modelInfoDO.getApiKey())
                .baseUrl(modelInfoDO.getBaseUrl())
                .manufacturerType(ModelManufacturerEnum.valueOf(modelInfoDO.getManufacturerCode()))
                .build();
        log.info("modelInfoDO:{}", modelInfoDO);
        ModelRequest request = ModelRequest.builder()
                .clientModelInfo(clientModelInfo)
                .inputText("你是什么大模型?")
                .build();
        ModelClientStrategy strategy = modelClientStrategyFactory.getStrategy(clientModelInfo);
        ModelResponse call = strategy.call(request);
        log.info("call: {}", call);
    }

    @Test
    void testOllamaModelCall() {
        ModelInfoDO modelInfoDO = modelInfoService.getById(6L);
        ClientModelInfo clientModelInfo = ClientModelInfo.builder()
                .modelId(modelInfoDO.getId())
                .model(modelInfoDO.getModel())
                .apiKey(modelInfoDO.getApiKey())
                .baseUrl(modelInfoDO.getBaseUrl())
                .manufacturerType(ModelManufacturerEnum.valueOf(modelInfoDO.getManufacturerCode()))
                .build();
        log.info("modelInfoDO:{}", modelInfoDO);
        ModelRequest request = ModelRequest.builder()
                .clientModelInfo(clientModelInfo)
                .inputText("你是什么大模型?")
                .build();
        ModelClientStrategy strategy = modelClientStrategyFactory.getStrategy(clientModelInfo);
        ModelResponse call = strategy.call(request);
        log.info("call: {}", call);
    }

    @Test
    void testQwen() {
        /**
         * 获取ChatClient
         * @return ChatClient
         */
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey("sk-ws-*****************************************")
                        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                        .build())
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model("qwen3.8-max")
                                .build()
                )
                .build();
    }
}
