package com.kant.llm.eval;

import com.kant.llm.eval.client.*;
import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import com.kant.llm.eval.dao.entity.ModelInfoDO;
import com.kant.llm.eval.service.ModelInfoService;
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
        ModelInfo modelInfo = ModelInfo.builder()
                .modelId(modelInfoDO.getId())
                .model(modelInfoDO.getModel())
                .apiKey(modelInfoDO.getApiKey())
                .baseUrl(modelInfoDO.getBaseUrl())
                .manufacturerType(ModelManufacturerEnum.valueOf(modelInfoDO.getManufacturerCode()))
                .build();
        log.info("modelInfoDO:{}", modelInfoDO);
        ModelRequest request = ModelRequest.builder()
                .modelInfo(modelInfo)
                .inputText("你是什么大模型?")
                .build();
        ModelClientStrategy strategy = modelClientStrategyFactory.getStrategy(modelInfo);
        ModelResponse call = strategy.call(request);
        log.info("call: {}", call);
    }
}
