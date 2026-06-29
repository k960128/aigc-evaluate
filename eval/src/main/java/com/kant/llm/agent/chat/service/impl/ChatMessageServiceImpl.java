package com.kant.llm.agent.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.agent.chat.constant.ChatMessageType;
import com.kant.llm.agent.chat.dao.entity.ChatMessageDO;
import com.kant.llm.agent.chat.dao.mapper.ChatMessageMapper;
import com.kant.llm.agent.chat.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI对话会话消息Service 实现类
 */
@Slf4j
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageDO> implements ChatMessageService {
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
}
