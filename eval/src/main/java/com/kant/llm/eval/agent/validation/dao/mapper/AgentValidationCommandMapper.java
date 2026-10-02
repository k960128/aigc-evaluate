package com.kant.llm.eval.agent.validation.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.validation.dao.entity.AgentValidationCommandDO;
import org.apache.ibatis.annotations.Mapper;
/** 停止命令幂等存储。 */
@Mapper
public interface AgentValidationCommandMapper extends BaseMapper<AgentValidationCommandDO> {}
