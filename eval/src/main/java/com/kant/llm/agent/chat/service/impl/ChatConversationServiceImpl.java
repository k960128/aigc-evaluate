package com.kant.llm.agent.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.agent.chat.constant.ChatConversationStatus;
import com.kant.llm.agent.chat.dao.entity.ChatConversationDO;
import com.kant.llm.agent.chat.dao.mapper.ChatConversationMapper;
import com.kant.llm.agent.chat.service.ChatConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AI对话会话表 Service 实现类
 */
@Slf4j
@Service
public class ChatConversationServiceImpl extends ServiceImpl<ChatConversationMapper, ChatConversationDO> implements ChatConversationService {

    /**
     * 创建新会话
     *
     * @param userId 用户ID
     * @param title  会话标题
     * @return 会话ID
     */
    @Override
    public String createConversation(String userId, String title) {
        String conversationId = UUID.randomUUID().toString().replace("-", "") + userId;

        ChatConversationDO conversation = new ChatConversationDO();
        conversation.setConversationId(conversationId);
        conversation.setUserId(userId);
        conversation.setTitle(title != null ? title : "新对话");
        conversation.setStatus(ChatConversationStatus.ACTIVE);
        conversation.setCreateTime(LocalDateTime.now());
        conversation.setUpdateTime(LocalDateTime.now());

        this.save(conversation);
        return conversationId;
    }

    @Override
    public Boolean updateTitle(String conversationId, String title) {
        return this.update(new LambdaUpdateWrapper<ChatConversationDO>()
                .eq(ChatConversationDO::getConversationId, conversationId)
                .set(ChatConversationDO::getTitle, title)
                .set(ChatConversationDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public List<ChatConversationDO> getConversationsByUserId(String userId) {
        return this.list(new LambdaQueryWrapper<ChatConversationDO>()
                .eq(ChatConversationDO::getUserId, userId)
                .ne(ChatConversationDO::getStatus, ChatConversationStatus.DELETED)
                .orderByDesc(ChatConversationDO::getUpdateTime));
    }

    @Override
    public ChatConversationDO getByConversationId(String conversationId) {
        return this.getOne(new LambdaQueryWrapper<ChatConversationDO>()
                .eq(ChatConversationDO::getConversationId, conversationId)
                .ne(ChatConversationDO::getStatus, ChatConversationStatus.DELETED));
    }

    @Override
    public boolean archiveConversation(String conversationId) {
        return this.update(new LambdaUpdateWrapper<ChatConversationDO>()
                .eq(ChatConversationDO::getConversationId, conversationId)
                .set(ChatConversationDO::getStatus, ChatConversationStatus.ARCHIVED)
                .set(ChatConversationDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public boolean deleteConversation(String conversationId) {
        return this.update(new LambdaUpdateWrapper<ChatConversationDO>()
                .eq(ChatConversationDO::getConversationId, conversationId)
                .set(ChatConversationDO::getStatus, ChatConversationStatus.DELETED)
                .set(ChatConversationDO::getUpdateTime, LocalDateTime.now()));
    }
}
