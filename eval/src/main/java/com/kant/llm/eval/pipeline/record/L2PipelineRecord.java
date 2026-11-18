package com.kant.llm.eval.pipeline.record;

import lombok.Builder;

@Builder
public record L2PipelineRecord(
        Boolean releaseStatus, // 是否放行
        L2EvaluateExecuteRecord l2EvaluateExecuteRecord // L2安全评估执行记录
) {
}
