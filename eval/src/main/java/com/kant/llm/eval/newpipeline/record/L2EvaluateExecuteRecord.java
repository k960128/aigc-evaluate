package com.kant.llm.eval.newpipeline.record;

import com.kant.llm.common.enums.SafetyEvaluateStatusEnums;
import lombok.Builder;

import java.util.List;

/**
 * L2安全评估执行器记录
 * 记录L2安全评估执行器的执行过程中的关键信息
 */
@Builder
public record L2EvaluateExecuteRecord(
        SafetyEvaluateStatusEnums status,// 安全评估状态
        Double score,// 当前分数
        List<String> evidenceChunks, // 证据块集合
        Long riskDetailsId // 风险小类ID
) {
}
