package com.kant.ai.infra.client.strategy;

import com.kant.ai.infra.client.*;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MockModelClientStrategy implements ModelClientStrategy {
    @Override
    public ModelResponse call(ModelRequest modelRequest) {
        log.info("mock data");
        // 生成模拟数据
        long startTime = System.currentTimeMillis();
        // 200 - 20000的随机数
        int random = ThreadLocalRandom.current().nextInt(200, 2000);
        try {
            // 模拟API调用
            TimeUnit.MILLISECONDS.sleep(random);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long endTime = System.currentTimeMillis();
        return ModelResponse.builder()
                .modelId(modelRequest.getClientModelInfo().getModelId())
                .respContent(modelRequest.getMockModelOutput())
                .elapsed(endTime - startTime)
                .build();
    }

    @Override
    public ModelManufacturerEnum getManufacturer() {
        return ModelManufacturerEnum.MOCK;
    }

    @Override
    public ModelConnectionResponse connection(ModelRequest modelRequest) {
        return null;
    }
}
