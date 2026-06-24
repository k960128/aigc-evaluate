package com.kant.llm.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.agent.constant.ChatConversationStatus;
import com.kant.llm.agent.dao.entity.ChatConversationDO;
import com.kant.llm.agent.dao.mapper.ChatConversationMapper;
import com.kant.llm.agent.service.ChatConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
}
