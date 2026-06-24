package com.kant.llm.agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.agent.dao.entity.ChatMessageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI对话消息表 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageDO> {
}
