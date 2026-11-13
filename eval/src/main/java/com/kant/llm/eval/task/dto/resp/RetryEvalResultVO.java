package com.kant.llm.eval.task.dto.resp;

public record RetryEvalResultVO(Long resultDetailId, Integer executionVersion,
                                String startStage, String requestId, String status) {
}
