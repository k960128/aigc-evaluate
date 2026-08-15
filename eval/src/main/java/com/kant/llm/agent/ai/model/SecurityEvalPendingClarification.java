package com.kant.llm.agent.ai.model;

import java.time.LocalDateTime;

/**
 * 安全评测助手待补槽状态。
 *
 * <p>当用户第一轮请求已经识别出明确安全评测意图，但缺少进入执行链路所需的关键槽位时，
 * 应用层会把当前意图识别结果、槽位校验结果和累计用户上下文暂存在该对象中。</p>
 *
 * <p>下一轮用户在同一个 {@code conversationId} 下补充信息时，系统会优先读取该状态，
 * 只做“槽位补充”，不再重新进行完整意图识别，避免把“result_detail_id 是 123”这类补充内容误判为越界请求。</p>
 *
 * @param conversationId           会话 ID，用于区分不同用户会话下的补槽状态
 * @param userContext              累计用户上下文，包含原始问题以及后续补充内容，后续进入 RAG 时作为完整问题使用
 * @param intentRecognitionResult  上一轮已经确认的安全评测意图识别结果
 * @param slotValidationResult     上一轮后端 Slot Validator 计算出的缺槽结果
 * @param createdAt                待补槽状态首次创建时间
 * @param updatedAt                待补槽状态最近更新时间
 */
public record SecurityEvalPendingClarification(
        String conversationId,
        String userContext,
        SecurityEvalIntentRecognitionResult intentRecognitionResult,
        SecurityEvalSlotValidationResult slotValidationResult,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
