package com.kant.agent.contract.evaluation;

import java.time.Instant;

import static com.kant.agent.contract.common.ContractChecks.*;

/**
 * 指定评测执行批次的即时状态快照。
 *
 * @param reference      本次查询对应的准确执行批次
 * @param rawStatusCode  现有评测系统的原始状态编码；未知时为空
 * @param totalCount     总样本数；尚无法获取时为空
 * @param finishedCount  已完成数量；含义沿用现有系统，不擅自重新计算
 * @param failedCount    失败数量；尚无法获取时为空
 * @param observedAt     获取该快照的时间
 */
public record EvaluationExecutionSnapshot(
        EvaluationTaskReference reference,
        Integer rawStatusCode,
        Long totalCount,
        Long finishedCount,
        Long failedCount,
        Instant observedAt
) {

    public EvaluationExecutionSnapshot {
        required(reference, "reference");
        required(observedAt, "observedAt");

        if (totalCount != null) {
            nonNegative(totalCount, "totalCount");
        }
        if (finishedCount != null) {
            nonNegative(finishedCount, "finishedCount");
        }
        if (failedCount != null) {
            nonNegative(failedCount, "failedCount");
        }
    }

    /**
     * 根据现有系统状态编码转换成智能体侧使用的状态。
     *
     * <p>未知编码不能推断为完成或失败。</p>
     */
    public State state() {
        if (rawStatusCode == null) {
            return State.UNKNOWN;
        }

        return switch (rawStatusCode) {
            case 0, 1, 2 -> State.QUEUED;
            case 3 -> State.RUNNING;
            case 4 -> State.COMPLETED;
            case 5 -> State.FAILED;
            case 6 -> State.STOPPED;
            default -> State.UNKNOWN;
        };
    }

    public enum State {
        /** 创建、初始化或等待执行。 */
        QUEUED,
        /** 正在执行。 */
        RUNNING,
        /** 已完成。 */
        COMPLETED,
        /** 执行异常。 */
        FAILED,
        /** 已停止。 */
        STOPPED,
        /** 状态未知，不能据此结束活动。 */
        UNKNOWN
    }
}