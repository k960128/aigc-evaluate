package com.kant.llm.eval.agent.redteam.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamTaskDO;
import org.apache.ibatis.annotations.Mapper;

/** 自动红队任务 Mapper。 */
@Mapper
public interface AgentRedTeamTaskMapper extends BaseMapper<AgentRedTeamTaskDO> {
}
