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
}
