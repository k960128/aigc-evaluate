package com.kant.ai.infra.client.strategy;


import com.kant.ai.infra.client.ModelClientStrategy;
import com.kant.ai.infra.client.ModelConnectionResponse;
import com.kant.ai.infra.client.ModelRequest;
import com.kant.ai.infra.client.ModelResponse;
import com.kant.ai.infra.enums.ModelManufacturerEnum;

public class SparkModelClientStrategy implements ModelClientStrategy {
    @Override
    public ModelResponse call(ModelRequest prompt) {
        return null;
    }


    @Override
    public ModelManufacturerEnum getManufacturer() {
        return ModelManufacturerEnum.SPARK;
    }

    @Override
    public ModelConnectionResponse connection(ModelRequest modelRequest) {
        return null;
    }
}
