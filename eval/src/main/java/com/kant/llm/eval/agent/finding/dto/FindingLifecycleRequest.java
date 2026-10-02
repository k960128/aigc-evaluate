package com.kant.llm.eval.agent.finding.dto;
import jakarta.validation.constraints.*;
/**
 * 归并/拆分和整改命令，不把人工意见混同为执行事实。
 * @param idempotencyKey 本根发现内变更幂等键
 * @param expectedVersion 根发现版本
 * @param type MERGE/SPLIT/START_WORK/READY_FOR_RETEST/CONFIRM_RESOLVED/REOPEN/ACCEPT_RISK
 * @param reason 人工归并依据、整改或风险接受依据
 * @param otherFindingId MERGE时被并入的根发现
 * @param expectedOtherVersion MERGE时另一根发现版本
 * @param occurrenceId SPLIT时被拆出的非根原始发生
 * @param ownerRef 整改动作负责人；首版只支持当前认证操作者ID
 * @param regressionRunId READY_FOR_RETEST/CONFIRM_RESOLVED所指的当前回归
 * @param repairSnapshotRef 同一当前回归冻结的新目标快照
 */
public record FindingLifecycleRequest(@NotBlank @Size(max=128) String idempotencyKey,@NotNull @Min(0) Long expectedVersion,
        @NotNull Type type,@NotBlank @Size(max=2000) String reason,@Size(max=96) String otherFindingId,
        @Min(0) Long expectedOtherVersion,@Size(max=96) String occurrenceId,@Size(max=128) String ownerRef,
        @Size(max=96) String regressionRunId,@Size(max=160) String repairSnapshotRef) {
    public enum Type { MERGE, SPLIT, START_WORK, READY_FOR_RETEST, CONFIRM_RESOLVED, REOPEN, ACCEPT_RISK }
}
