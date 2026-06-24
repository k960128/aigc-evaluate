package com.kant.llm.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.agent.dao.entity.ChatConversationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI对话会话表 Mapper
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversationDO> {
}
