package com.kant.llm.rag;

import com.kant.ai.infra.client.*;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import com.kant.llm.eval.basesource.dao.entity.ModelInfoDO;
import com.kant.llm.eval.basesource.service.ModelInfoService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
}
