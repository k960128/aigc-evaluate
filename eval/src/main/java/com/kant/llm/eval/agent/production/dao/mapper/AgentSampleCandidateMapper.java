package com.kant.llm.eval.agent.production.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleCandidateDO;
import org.apache.ibatis.annotations.Mapper;
/**
 * agent_sample_candidate表的内部MyBatis-Plus映射，不直接暴露为业务接口。
 * 查询须限制当前主体或先授权批次；状态变更由结算和人工审核服务统一维护。
 * BaseMapper本身不提供业务授权，不能绕过Service使用通用CRUD破坏历史事实。
 */
@Mapper
public interface AgentSampleCandidateMapper extends BaseMapper<AgentSampleCandidateDO> {}
