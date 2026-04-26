package com.kant.llm.eval.client;

import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import org.springframework.ai.chat.client.ChatClient;

/**
 * 大模型策略接口
 */
public interface ModelClientStrategy {


    ModelResponse call(ModelRequest modelRequest);

    /**
     * 大模型厂商标识
     */
    ModelManufacturerEnum getManufacturer();
}
