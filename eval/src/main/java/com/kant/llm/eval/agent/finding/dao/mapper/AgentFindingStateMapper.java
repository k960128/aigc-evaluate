package com.kant.llm.eval.agent.finding.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.finding.dao.entity.AgentFindingStateDO;
import org.apache.ibatis.annotations.Mapper;
/** 发现控制面Mapper。 */
@Mapper
public interface AgentFindingStateMapper extends BaseMapper<AgentFindingStateDO> {}
