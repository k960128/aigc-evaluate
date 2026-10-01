package com.kant.llm.eval.agent.redteam.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamSessionCheckpointDO;
import org.apache.ibatis.annotations.Mapper;

/** D03 会话检查点 Mapper。 */
@Mapper
public interface AgentRedTeamSessionCheckpointMapper
        extends BaseMapper<AgentRedTeamSessionCheckpointDO> {
}
