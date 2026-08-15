package com.kant.ai.infra.judge;

import com.kant.ai.infra.enums.ModelManufacturerEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeModelResponse {

    /**
     * 裁判模型原始返回内容，由 eval 模块继续做 JSON 解析和降级处理。
     */
    private String rawContent;

    /**
     * 裁判模型调用耗时，单位毫秒。
     */
    private Long elapsed;

    /**
     * 实际命中的裁判模型厂商。
     */
    private ModelManufacturerEnum manufacturer;

    /**
     * 实际调用的裁判模型名称。
     */
    private String model;
}
