package com.kant.llm.agent.ai.model;

import com.kant.llm.agent.chat.constant.ChatSource;

/**
 * 安全评测助手 RAG 对话参数。
 *
 * <p>该对象承载安全评测链路在进入 RAG 阶段后需要的上下文，
 * 包括用户会话信息、消息 ID、原始问题、意图识别结果和槽位校验结果。</p>
 */
public record SecurityEvalChatParam(
        String userId,
        String conversationId,
        String messageId,
        String content,
        String assistantMessageId,
        SecurityEvalIntentRecognitionResult intentRecognitionResult,
        SecurityEvalSlotValidationResult slotValidationResult,
        ChatSource chatSource
) {
}
