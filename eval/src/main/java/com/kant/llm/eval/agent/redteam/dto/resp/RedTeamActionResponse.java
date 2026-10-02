package com.kant.llm.eval.agent.redteam.dto.resp;

import com.kant.agent.contract.redteam.RedTeamActionRecord;

import java.time.Instant;
import java.util.List;

/**
 * 自动红队动作轨迹响应。
 *
 * <p>只返回输入摘要和证据引用，不直接返回输入正文或目标响应正文；正文通过
 * D01 证据接口按权限读取。</p>
 */
public record RedTeamActionResponse(
        String actionId,
        String attemptId,
        int sequence,
        String actionType,
        String strategyVersion,
        String inputHash,
        String reasonSummary,
        List<String> observationRefs,
        String suggestedStopReason,
        String actualStopReason,
        String inputEvidenceRef,
        String probeId,
        String executionId,
        String executionStatus,
        String responseEvidenceRef,
        String errorCode,
        boolean riskObserved,
        String actionStatus,
        Instant createdAt) {

    /** 将动作事实转换为 API 响应。 */
    public static RedTeamActionResponse from(RedTeamActionRecord action) {
        return new RedTeamActionResponse(
                action.actionId(),
                action.attemptId(),
                action.sequence(),
                action.actionType().name(),
                action.strategyVersion(),
                action.inputHash(),
                action.reasonSummary(),
                action.observationRefs(),
                action.suggestedStopReason() == null ? null : action.suggestedStopReason().name(),
                action.actualStopReason() == null ? null : action.actualStopReason().name(),
                action.inputEvidenceRef(),
                action.probeId(),
                action.executionId(),
                action.executionStatus() == null ? null : action.executionStatus().name(),
                action.responseEvidenceRef(),
                action.errorCode(),
                action.riskObserved(),
                action.actionStatus(),
                action.createdAt());
    }
}
