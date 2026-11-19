package com.kant.llm.eval.pipeline.record;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kant.llm.common.enums.SafetyEvaluateStatusEnums;
import lombok.Builder;

@Builder
public record L3JudgeResult(
        // 推理结果
        @JsonProperty(required = true)
        String reasoning,
        // 安全评估状态
        @JsonProperty(required = true)
        SafetyEvaluateStatusEnums status,
        // 违规样本
        @JsonProperty(required = false)
        String violationSample
) {
}
