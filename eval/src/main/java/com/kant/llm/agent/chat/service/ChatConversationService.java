package com.kant.llm.agent.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.agent.chat.dao.entity.ChatConversationDO;

import java.util.List;

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


    /**
     * 根据用户ID获取会话列表
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ChatConversationDO> getConversationsByUserId(String userId);

    /**
     * 根据会话ID获取会话
     *
     * @param conversationId 会话ID
     * @return 会话信息
     */
    ChatConversationDO getByConversationId(String conversationId);

    /**
     * 归档会话
     *
     * @param conversationId 会话ID
     * @return 是否成功
     */
    boolean archiveConversation(String conversationId);

    /**
     * 删除会话
     *
     * @param conversationId 会话ID
     * @return 是否成功
     */
    boolean deleteConversation(String conversationId);
}
