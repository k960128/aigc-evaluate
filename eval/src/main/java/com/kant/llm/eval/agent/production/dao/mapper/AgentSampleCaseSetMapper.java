package com.kant.llm.eval.agent.production.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleCaseSetDO;
import org.apache.ibatis.annotations.Mapper;
/**
 * agent_sample_case_set表的内部MyBatis-Plus映射，不直接暴露为业务接口。
 * 主体内创建幂等键防重复，读取需校验租户、操作者及集合版本。
 * BaseMapper本身不提供业务授权，不能绕过Service使用通用CRUD破坏历史事实。
 */
@Mapper
public interface AgentSampleCaseSetMapper extends BaseMapper<AgentSampleCaseSetDO> {}
