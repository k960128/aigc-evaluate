package com.kant.agent.contract.probe;

import com.kant.agent.contract.common.ContractChecks;

import java.time.Instant;

/**
 * 探测取消意图的受理结果。
 *
 * @param reference             探测执行引用
 * @param status                受理取消意图后的执行状态
 * @param cancellationRequested 是否已经记录取消意图
 * @param requestedAt           首次记录取消意图的时间；未记录时为空
 */
public record ProbeCancellation(
        ProbeExecutionReference reference,
        ProbeExecutionStatus status,
        boolean cancellationRequested,
        Instant requestedAt) {

    /**
     * 校验取消结果的一致性。
     */
    public ProbeCancellation {
        reference = ContractChecks.required(reference, "reference");
        status = ContractChecks.required(status, "status");
        if (cancellationRequested) {
            requestedAt = ContractChecks.required(requestedAt, "requestedAt");
        }
    }
}
