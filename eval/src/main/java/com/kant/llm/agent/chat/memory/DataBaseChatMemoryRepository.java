package com.kant.llm.agent.chat.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.llm.agent.chat.dao.entity.ChatMessageDO;
import com.kant.llm.agent.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.kant.llm.agent.chat.constant.ChatMessageType.ASSISTANT;
import static com.kant.llm.agent.chat.constant.ChatMessageType.USER;

/**
 * 基于数据库 + Redis 的 Spring AI ChatMemoryRepository 实现。
 *
 * 使用 Redis 缓存 + 数据库持久化的组合策略，只保留最近 10 条消息：
 * - findByConversationId(): 优先从 Redis 读取；缓存未命中时从数据库加载历史消息并写入 Redis
 * - saveAll(): 更新 Redis 缓存，保证同一轮 ChatMemory 调用中的上下文一致
 * - deleteByConversationId(): 删除 Redis 缓存，并删除数据库中的会话消息
 *
 * 注意：消息的最终持久化仍由 ChatMessageService 在流式对话完成时处理，
 * 比如 saveUserMessage、saveAssistantMessage、updateContent 等。
 *
 * Redis Key 格式: know-engine:chat-memory:{conversationId}
 * TTL: 1 小时
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataBaseChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMessageService chatMessageService;

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    private static final String REDIS_KEY_PREFIX = "eval:chat-memory:";

    /**
     * 最大保留消息条数。
     */
    private static final int MAX_MESSAGES = 10;

    /**
     * Redis 缓存过期时间，单位：小时。
     */
    private static final long CACHE_TTL_HOURS = 1;

    private static final TypeReference<List<MemoryMessage>> MEMORY_MESSAGE_LIST_TYPE =
            new TypeReference<>() {
            };

    @Override
    public List<String> findConversationIds() {
        try {
            return chatMessageService.lambdaQuery()
                    .select(ChatMessageDO::getConversationId)
                    .isNotNull(ChatMessageDO::getConversationId)
                    .groupBy(ChatMessageDO::getConversationId)
                    .list()
                    .stream()
                    .map(ChatMessageDO::getConversationId)
                    .filter(Objects::nonNull)
                    .filter(conversationId -> !conversationId.isEmpty())
                    .toList();
        } catch (Exception e) {
            log.warn("查询会话ID列表失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = buildKey(conversationId);

        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isEmpty()) {
                return new ArrayList<>(deserializeMessages(json));
            }
        } catch (Exception e) {
            log.warn("Redis 读取聊天记忆失败, conversationId={}, 将从数据库加载", conversationId, e);
        }

        List<Message> messages = loadFromDatabase(conversationId);
        saveToRedis(key, messages);
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        List<Message> trimmed = trimMessages(messages);
        saveToRedis(buildKey(conversationId), trimmed);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        try {
            stringRedisTemplate.delete(buildKey(conversationId));
        } catch (Exception e) {
            log.warn("Redis 删除聊天记忆失败, conversationId={}", conversationId, e);
        }

        chatMessageService.deleteMessagesByConversationId(conversationId);
    }

    /**
     * 清除指定会话的缓存。
     * 可在新一轮对话开始前调用，确保从数据库重新加载最新消息。
     */
    public void evictCache(String conversationId) {
        try {
            stringRedisTemplate.delete(buildKey(conversationId));
        } catch (Exception e) {
            log.warn("Redis 清除聊天记忆缓存失败, conversationId={}", conversationId, e);
        }
    }

    /**
     * 从数据库加载历史消息。
     * 这里只加载 USER / ASSISTANT 消息，避免其他过程消息污染对话上下文。
     */
    private List<Message> loadFromDatabase(String conversationId) {
        List<ChatMessageDO> dbMessages = chatMessageService.getRecentMessages(conversationId, MAX_MESSAGES);

        List<Message> messages = new ArrayList<>();
        for (ChatMessageDO dbMessage : dbMessages) {
            String content = dbMessage.getContent();
            if (content == null || content.isEmpty()) {
                continue;
            }

            if (dbMessage.getType() == USER) {
                messages.add(new UserMessage(content));
            } else if (dbMessage.getType() == ASSISTANT) {
                messages.add(new AssistantMessage(content));
            }
        }

        return messages;
    }

    /**
     * 序列化消息列表并保存到 Redis，同时设置 TTL。
     */
    private void saveToRedis(String key, List<Message> messages) {
        try {
            String json = objectMapper.writeValueAsString(toMemoryMessages(messages));
            stringRedisTemplate.opsForValue().set(key, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis 保存聊天记忆失败, key={}", key, e);
        }
    }

    private List<Message> deserializeMessages(String json) throws Exception {
        List<MemoryMessage> memoryMessages = objectMapper.readValue(json, MEMORY_MESSAGE_LIST_TYPE);

        List<Message> messages = new ArrayList<>();
        for (MemoryMessage memoryMessage : memoryMessages) {
            Message message = toSpringAiMessage(memoryMessage);
            if (message != null) {
                messages.add(message);
            }
        }

        return messages;
    }

    private List<MemoryMessage> toMemoryMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        return messages.stream()
                .filter(Objects::nonNull)
                .map(message -> new MemoryMessage(message.getMessageType(), message.getText()))
                .toList();
    }

    private Message toSpringAiMessage(MemoryMessage memoryMessage) {
        if (memoryMessage == null || memoryMessage.messageType == null) {
            return null;
        }

        String text = memoryMessage.text == null ? "" : memoryMessage.text;

        return switch (memoryMessage.messageType) {
            case USER -> new UserMessage(text);
            case ASSISTANT -> new AssistantMessage(text);
            default -> throw new IllegalStateException("Unexpected value: " + memoryMessage.messageType);
        };
    }

    /**
     * 截断消息列表，只保留最近 MAX_MESSAGES 条。
     */
    private List<Message> trimMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        if (messages.size() <= MAX_MESSAGES) {
            return new ArrayList<>(messages);
        }

        return new ArrayList<>(messages.subList(messages.size() - MAX_MESSAGES, messages.size()));
    }

    private String buildKey(String conversationId) {
        return REDIS_KEY_PREFIX + conversationId;
    }

    private static class MemoryMessage {

        public MessageType messageType;

        public String text;

        public MemoryMessage(MessageType messageType, String text) {
            this.messageType = messageType;
            this.text = text;
        }
    }
}
