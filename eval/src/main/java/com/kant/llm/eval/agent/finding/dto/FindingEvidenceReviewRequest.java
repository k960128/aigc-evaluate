package com.kant.llm.eval.agent.finding.dto;
import jakarta.validation.constraints.*;
/**
 * 保存一次证据一致性检查的请求，不触发目标调用。
 * @param idempotencyKey 本发现内幂等键，同键重试保留原请求
 * @param expectedVersion 发现详情返回的当前版本
 */
public record FindingEvidenceReviewRequest(@NotBlank @Size(max=128) @Pattern(regexp="[A-Za-z0-9_.:-]+") String idempotencyKey,
        @NotNull @Min(0) Long expectedVersion) {}
