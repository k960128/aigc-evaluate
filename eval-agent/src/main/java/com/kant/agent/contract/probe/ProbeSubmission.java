package com.kant.agent.contract.probe;

import com.kant.agent.contract.common.ContractChecks;

import java.time.Instant;

/**
 * 探测提交结果。
 *
 * @param reference     探测和执行引用
 * @param status        返回时的执行状态
 * @param newlyAccepted 本次请求是否新建了执行意图；幂等重放时为 {@code false}
 * @param acceptedAt    首次受理时间
 */
public record ProbeSubmission(
        ProbeExecutionReference reference,
        ProbeExecutionStatus status,
        boolean newlyAccepted,
        Instant acceptedAt) {

    /**
     * 校验提交结果。
     */
    public ProbeSubmission {
        reference = ContractChecks.required(reference, "reference");
        status = ContractChecks.required(status, "status");
        acceptedAt = ContractChecks.required(acceptedAt, "acceptedAt");
    }
}
