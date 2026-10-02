package com.kant.llm.eval.agent.redteam.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamSessionDO;
import org.apache.ibatis.annotations.Mapper;

/** D03 目标会话 Mapper。 */
@Mapper
public interface AgentRedTeamSessionMapper extends BaseMapper<AgentRedTeamSessionDO> {
}
