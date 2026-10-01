package com.kant.llm.eval.agent.production.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleRunReceiptDO;
import org.apache.ibatis.annotations.Mapper;

/** 请求回执映射；唯一索引为taskId + idempotencyKey。 */
@Mapper
public interface AgentSampleRunReceiptMapper extends BaseMapper<AgentSampleRunReceiptDO> {}
