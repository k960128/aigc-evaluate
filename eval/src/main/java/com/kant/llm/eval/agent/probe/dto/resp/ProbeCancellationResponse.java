package com.kant.llm.eval.agent.probe.dto.resp;

import com.kant.agent.contract.probe.ProbeCancellation;

import java.time.Instant;

/**
 * 探测取消意图响应。
 *
 * @param probeId              逻辑探测标识
 * @param executionId          物理执行标识
 * @param status               当前执行状态
 * @param cancellationRequested 是否已经记录取消意图
 * @param requestedAt          首次记录取消意图的时间
 */
public record ProbeCancellationResponse(
        String probeId,
        String executionId,
        String status,
        boolean cancellationRequested,
        Instant requestedAt) {

    /** 将领域取消结果转换为 API 响应。 */
    public static ProbeCancellationResponse from(ProbeCancellation cancellation) {
        return new ProbeCancellationResponse(
                cancellation.reference().probeId(),
                cancellation.reference().executionId(),
                cancellation.status().name(),
                cancellation.cancellationRequested(),
                cancellation.requestedAt());
    }
}
