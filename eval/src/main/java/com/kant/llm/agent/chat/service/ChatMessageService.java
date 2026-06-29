package com.kant.llm.agent.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.agent.chat.dao.entity.ChatMessageDO;

/**
 *  AI对话消息表 Service 接口
 */
public interface ChatMessageService extends IService<ChatMessageDO> {
    /**
     * 保存用户消息
     *
     * @param conversationId 会话ID
     * @param content        消息内容
     * @return 消息ID
     */
    String saveUserMessage(String conversationId, String content);

    /**
     * 更新问题改写结果（transformContent）
     *
     * @param messageId        消息ID
     * @param transformContent 改写后的问题
     */
    void updateTransformContent(String messageId, String transformContent);

    /**
     * 对话完成后保存 Assistant 消息（插入新记录）
     *
     * @param conversationId 会话ID
     * @return 新消息ID
     */
    String saveAssistantMessage(String conversationId);

}
