package com.kant.llm.eval.agent.redteam.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamFindingDO;
import org.apache.ibatis.annotations.Mapper;

/** 自动红队疑似发现 Mapper。 */
@Mapper
public interface AgentRedTeamFindingMapper extends BaseMapper<AgentRedTeamFindingDO> {
}
