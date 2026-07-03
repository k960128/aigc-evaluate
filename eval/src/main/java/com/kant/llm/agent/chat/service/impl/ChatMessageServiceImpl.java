package com.kant.llm.agent.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.agent.chat.constant.ChatMessageType;
import com.kant.llm.agent.chat.dao.entity.ChatMessageDO;
import com.kant.llm.agent.chat.dao.mapper.ChatMessageMapper;
import com.kant.llm.agent.chat.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AI对话会话消息Service 实现类
 */
@Slf4j
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageDO> implements ChatMessageService {
    @Override
    public List<ChatMessageDO> getMessagesByConversationId(String conversationId) {
        return this.list(new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getConversationId, conversationId)
                .orderByAsc(ChatMessageDO::getCreateTime));
    }

    @Override
    public ChatMessageDO getByMessageId(String messageId) {
        return this.getOne(new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getMessageId, messageId));
    }

    @Override
    public String saveUserMessage(String conversationId, String content) {
        String messageId = UUID.randomUUID().toString().replace("-", "");

        ChatMessageDO message = new ChatMessageDO();
        message.setMessageId(messageId);
        message.setConversationId(conversationId);
        message.setType(ChatMessageType.USER);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());

        this.save(message);
        return messageId;
    }

    @Override
    public void updateTransformContent(String messageId, String transformContent) {
        ChatMessageDO update = new ChatMessageDO();
        update.setTransformContent(transformContent);
        this.update(update, new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getMessageId, messageId));
    }

    @Override
    public void updateRagReferences(String messageId, List<ChatMessageDO.RagReference> ragReferences) {
        ChatMessageDO update = new ChatMessageDO();
        update.setRagReferences(ragReferences);
        this.update(update, new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getMessageId, messageId));
    }

    @Override
    public void updateContent(String messageId, String content) {
        ChatMessageDO update = new ChatMessageDO();
        update.setContent(content);
        this.update(update, new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getMessageId, messageId));
    }

    @Override
    public String saveAssistantMessage(String conversationId) {
        String messageId = UUID.randomUUID().toString().replace("-", "");

        ChatMessageDO message = new ChatMessageDO();
        message.setMessageId(messageId);
        message.setConversationId(conversationId);
        message.setType(ChatMessageType.ASSISTANT);
        message.setCreateTime(LocalDateTime.now());

        this.save(message);
        return messageId;
    }

    @Override
    public Boolean deleteMessagesByConversationId(String conversationId) {
        return this.remove(new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getConversationId, conversationId));
    }

    @Override
    public List<ChatMessageDO> getRecentMessages(String conversationId, int limit) {
        // 查询最新的 limit+2 条，排除最新的2条（当前轮次刚保存的user消息和空assistant消息）
        Page<ChatMessageDO> page = this.page(
                new Page<>(1, limit + 2),
                new LambdaQueryWrapper<ChatMessageDO>()
                        .eq(ChatMessageDO::getConversationId, conversationId)
                        .orderByDesc(ChatMessageDO::getCreateTime)
        );

        List<ChatMessageDO> records = page.getRecords();
        // 去掉最新的2条
        if (records.size() > 2) {
            records = records.subList(2, records.size());
        } else {
            return new java.util.ArrayList<>();
        }

        // 返回列表需要反转，使其按时间正序排列
        java.util.Collections.reverse(records);
        return records;
    }
}
