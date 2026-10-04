package com.kant.llm.eval.agent.production.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleReviewDO;
import org.apache.ibatis.annotations.Mapper;
/**
 * agent_sample_review表的内部MyBatis-Plus映射，不直接暴露为业务接口。
 * 写入由审核服务的候选行锁和事务保护，不允许通用接口改写历史意见。
 * BaseMapper本身不提供业务授权，不能绕过Service使用通用CRUD破坏历史事实。
 */
@Mapper
public interface AgentSampleReviewMapper extends BaseMapper<AgentSampleReviewDO> {}
