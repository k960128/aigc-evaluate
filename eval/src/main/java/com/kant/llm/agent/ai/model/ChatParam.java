package com.kant.llm.agent.ai.model;

import com.kant.llm.agent.chat.constant.ChatSource;

public record ChatParam(String userId, String conversationId, String messageId, String content, String assistantMessageId,
                        IntentRecognitionResult intentRecognitionResult, ChatSource chatSource) {
}