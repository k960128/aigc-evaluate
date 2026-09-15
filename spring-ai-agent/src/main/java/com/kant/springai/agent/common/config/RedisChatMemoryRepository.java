package com.kant.springai.agent.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Redis List 的会话记忆仓库。
 *
 * <p>Key 结构：{prefix}{conversationId} -> List&lt;JSON&gt;
 * <p>每个元素是一条消息的 JSON 表示（MessageDocument），顺序即时间顺序。
 */
@Slf4j
@Component
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final Duration ttl;

    public RedisChatMemoryRepository(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.ai.chat.memory.redis.key-prefix:spring:ai:chat:memory:}") String keyPrefix,
            @Value("${spring.ai.chat.memory.redis.ttl:7d}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.ttl = ttl;
    }

    @Override
    public List<String> findConversationIds() {
        Set<String> ids = new LinkedHashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(keyPrefix + "*")
                .count(200)
                .build();

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    ids.add(key.substring(keyPrefix.length()));
                }
            } catch (Exception e) {
                throw new IllegalStateException("扫描会话 ID 失败", e);
            }
            return null;
        });

        return new ArrayList<>(ids);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = buildKey(conversationId);
        List<String> rawList = redisTemplate.opsForList().range(key, 0, -1);
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }

        List<Message> messages = new ArrayList<>(rawList.size());
        for (String raw : rawList) {
            try {
                MessageDocument doc = objectMapper.readValue(raw, MessageDocument.class);
                messages.add(doc.toMessage());
            } catch (Exception e) {
                log.warn("反序列化消息失败，已跳过。conversationId={}, raw={}", conversationId, raw, e);
            }
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = buildKey(conversationId);

        List<String> payload = new ArrayList<>(messages.size());
        for (Message message : messages) {
            try {
                payload.add(objectMapper.writeValueAsString(MessageDocument.from(message)));
            } catch (Exception e) {
                log.error("序列化消息失败，已跳过。conversationId={}, message={}", conversationId, message, e);
            }
        }

        redisTemplate.delete(key);
        if (!payload.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, payload);
            if (ttl != null && !ttl.isZero()) {
                redisTemplate.expire(key, ttl);
            }
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(buildKey(conversationId));
    }

    private String buildKey(String conversationId) {
        return keyPrefix + conversationId;
    }

    /**
     * 用于 JSON 序列化的消息载体。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageDocument {
        private String type;
        private String text;
        private Map<String, Object> metadata;

        public static MessageDocument from(Message message) {
            return new MessageDocument(
                    message.getMessageType().name(),
                    message.getText(),
                    message.getMetadata()
            );
        }

        public Message toMessage() {
            MessageType messageType = MessageType.valueOf(type);
            return switch (messageType) {
                case USER -> new UserMessage(text);
                case ASSISTANT -> new AssistantMessage(text);
                case SYSTEM -> new SystemMessage(text);
                default -> throw new IllegalArgumentException("Unsupported message type: " + type);
            };
        }
    }
}