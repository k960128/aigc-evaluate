package com.kant.agent.contract.validation;

import com.kant.agent.contract.assessment.Assessment;

import java.util.List;

/**
 * 一步真实执行结果，不含输入和响应正文。
 * @param caseIndex 用例在计划中的0基序号
 * @param stepSequence 用例内1基步骤
 * @param executionId 新物理执行标识
 * @param assessment 新追加判定，不能覆盖原始探索判定
 * @param evidenceRefs 新执行输入和响应证据
 */
public record ValidationStepResult(int caseIndex, int stepSequence, String executionId, Assessment assessment,
                                   List<String> evidenceRefs) {
    public ValidationStepResult {
        evidenceRefs = List.copyOf(evidenceRefs);
    }
}
