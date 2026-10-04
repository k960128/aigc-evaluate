package com.kant.llm.eval.agent.production.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleTaskDO;
import org.apache.ibatis.annotations.Mapper;
/**
 * agent_sample_task表的内部MyBatis-Plus映射，不直接暴露为业务接口。
 * 创建/重置共享主体内幂等键，启停和调度由服务事务协调，不允许直接覆盖冻结配置。
 * BaseMapper本身不提供业务授权，不能绕过Service使用通用CRUD破坏历史事实。
 */
@Mapper
public interface AgentSampleTaskMapper extends BaseMapper<AgentSampleTaskDO> {}
