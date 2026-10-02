package com.kant.llm.eval.agent.probe.dto.resp;

import com.kant.agent.contract.probe.ProbeSubmission;

import java.time.Instant;

/**
 * 探测提交响应。
 *
 * @param probeId      逻辑探测标识
 * @param executionId  本次物理执行标识，后续查询和取证均使用该字段
 * @param status       返回时的执行状态
 * @param newlyAccepted 本次请求是否新建执行；幂等重放时为 {@code false}
 * @param acceptedAt   首次受理时间
 */
public record ProbeSubmissionResponse(
        String probeId,
        String executionId,
        String status,
        boolean newlyAccepted,
        Instant acceptedAt) {

    /** 将领域提交结果转换为 API 响应。 */
    public static ProbeSubmissionResponse from(ProbeSubmission submission) {
        return new ProbeSubmissionResponse(
                submission.reference().probeId(),
                submission.reference().executionId(),
                submission.status().name(),
                submission.newlyAccepted(),
                submission.acceptedAt());
    }
}
