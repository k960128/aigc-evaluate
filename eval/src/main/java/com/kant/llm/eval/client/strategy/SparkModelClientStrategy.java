package com.kant.llm.eval.client.strategy;

import com.kant.llm.eval.client.ModelClientStrategy;
import com.kant.llm.eval.client.ModelConnectionResponse;
import com.kant.llm.eval.client.ModelRequest;
import com.kant.llm.eval.client.ModelResponse;
import com.kant.llm.eval.common.enums.ModelManufacturerEnum;

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
