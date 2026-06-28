package com.kant.llm.agent.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.agent.chat.dao.entity.ChatConversationDO;

/**
 * AI对话会话表 Service 接口
 */
public interface ChatConversationService extends IService<ChatConversationDO> {
    /**
     * 创建新会话
     *
     * @param userId 用户ID
     * @param title  会话标题
     * @return 会话ID
     */
    String createConversation(String userId, String title);

    /**
     * 更新会话标题
     *
     * @param conversationId 会话ID
     * @param title          新标题
     * @return 是否成功
     */
    Boolean updateTitle(String conversationId, String title);
}
