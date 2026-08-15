package com.kant.llm.agent.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 通用会话服务
 */
@Slf4j
@Component
public class CommonChatService {

    private final ChatClient chatClient;

    public CommonChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 通用会话服务 - 流式返回会话内容
     * @param userId 用户ID
     * @param userMessage 用户问题
     * @param conversationId 会话ID
     * @return 流式返回会话内容的Flux对象
     */
    public Flux<String> chatStream(String userId, String userMessage, String conversationId) {
        return chatClient.prompt()
                .system("你是一个智能客服，请回答用户的问题。")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(u -> u.text(userMessage))
                .stream().content();
    }
}
