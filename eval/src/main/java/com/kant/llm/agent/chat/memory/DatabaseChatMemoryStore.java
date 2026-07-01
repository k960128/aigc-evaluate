package com.kant.llm.agent.chat.memory;

import com.kant.llm.agent.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于数据库 + Redis 的 ChatMemoryStore 实现
 * <p>
 * 使用 Redis 缓存 + 数据库持久化的组合策略，只保留最近 10 条消息：
 * - get(): 优先从 Redis 读取；缓存未命中时从数据库加载历史消息并写入 Redis
 * - 消息的最终持久化由 ChatMessageService 在流式对话完成时处理（saveUserMessage/updateContent）
 * <p>
 * Redis Key 格式: know-engine:chat-memory:{conversationId}
 * TTL: 1 小时（自动过期，避免冷数据长期占用内存）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseChatMemoryStore implements ChatMemory {

    private final ChatMessageService chatMessageService;

    private final StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_KEY_PREFIX = "eval:chat-memory:";

    /**
     * 最大保留消息条数
     */
    private static final int MAX_MESSAGES = 10;

    /**
     * Redis 缓存过期时间（小时）
     */
    private static final long CACHE_TTL_HOURS = 1;


    @Override
    public void add(String conversationId, List<Message> messages) {

    }

    @Override
    public List<Message> get(String conversationId) {
        return List.of();
    }

    @Override
    public void clear(String conversationId) {

    }

    @Override
    public void add(String conversationId, Message message) {
        ChatMemory.super.add(conversationId, message);
    }
}
