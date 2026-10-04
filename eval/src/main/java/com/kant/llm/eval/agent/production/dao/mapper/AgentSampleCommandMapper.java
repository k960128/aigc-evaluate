package com.kant.llm.eval.agent.production.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.production.dao.entity.AgentSampleCommandDO;
import org.apache.ibatis.annotations.Mapper;
/**
 * agent_sample_command表的内部MyBatis-Plus映射，不直接暴露为业务接口。
 * 资源归属由服务层先校验，再按资源和幂等键回放首次结果。
 * BaseMapper本身不提供业务授权，不能绕过Service使用通用CRUD破坏历史事实。
 */
@Mapper
public interface AgentSampleCommandMapper extends BaseMapper<AgentSampleCommandDO> {}
