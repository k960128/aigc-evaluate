package com.kant.llm.eval.agent.redteam.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamRunDO;
import org.apache.ibatis.annotations.Mapper;

/** 自动红队运行 Mapper。 */
@Mapper
public interface AgentRedTeamRunMapper extends BaseMapper<AgentRedTeamRunDO> {
}
