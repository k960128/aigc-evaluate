package com.kant.llm.eval.pipeline.record;

import com.kant.llm.common.enums.SafetyEvaluateStatusEnums;
import lombok.Builder;

import java.util.List;

/**
 * L2安全评估执行器记录
 * 记录L2安全评估执行器的执行过程中的关键信息
 */
@Builder
public record L2EvaluateExecuteRecord(
        /*
          L2 状态枚举
         */
        SafetyEvaluateStatusEnums.L2SafetyEvaluateStatusEnums status,
        /*
          当前分数
         */
        Double score,
        /*
          证据块集合
         */
        List<String> evidenceChunks,

        /*
          风险小类ID
         */
        Long riskDetailsId // 风险小类ID
) {
}
