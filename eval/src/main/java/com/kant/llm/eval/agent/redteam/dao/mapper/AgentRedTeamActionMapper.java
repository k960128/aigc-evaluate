package com.kant.llm.eval.agent.redteam.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamActionDO;
import org.apache.ibatis.annotations.Mapper;

/** 自动红队动作 Mapper。 */
@Mapper
public interface AgentRedTeamActionMapper extends BaseMapper<AgentRedTeamActionDO> {
}
