package com.kant.llm.agent.chat.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.agent.chat.dao.entity.ChatMessageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI对话消息表 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageDO> {
}
