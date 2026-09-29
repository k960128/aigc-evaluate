package com.kant.llm.eval.agent.probe.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.probe.dao.entity.AgentProbeExecutionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动态探测执行事实 Mapper。
 */
@Mapper
public interface AgentProbeExecutionMapper extends BaseMapper<AgentProbeExecutionDO> {
}
