package com.kant.llm.eval.agent.production.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleRunReceiptDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * agent_sample_run_receipt表的内部MyBatis-Plus映射，不直接暴露为业务接口。
 * 唯一索引为taskId与idempotencyKey，归属从生产计划继承并由服务层校验。
 * BaseMapper本身不提供业务授权，不能绕过Service使用通用CRUD破坏历史事实。
 */
@Mapper
public interface AgentSampleRunReceiptMapper extends BaseMapper<AgentSampleRunReceiptDO> {}
