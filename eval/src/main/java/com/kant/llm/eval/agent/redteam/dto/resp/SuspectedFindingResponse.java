package com.kant.llm.eval.agent.redteam.dto.resp;

import com.kant.agent.contract.redteam.SuspectedFinding;

import java.time.Instant;
import java.util.List;

/**
 * D02 疑似发现响应。
 *
 * <p>{@code verificationStatus=SUSPECTED} 只表示规则信号被观察到，尚未经过
 * D04 的独立复核和复现。</p>
 */
public record SuspectedFindingResponse(
        String findingId,
        String taskId,
        String runId,
        String attemptId,
        String actionId,
        String riskId,
        List<String> evidenceRefs,
        String reason,
        String verificationStatus,
        Instant createdAt) {

    /** 将疑似发现转换为 API 响应。 */
    public static SuspectedFindingResponse from(SuspectedFinding finding) {
        return new SuspectedFindingResponse(
                finding.findingId(),
                finding.taskId(),
                finding.runId(),
                finding.attemptId(),
                finding.actionId(),
                finding.riskId(),
                finding.evidenceRefs(),
                finding.reason(),
                finding.verificationStatus(),
                finding.createdAt());
    }
}
