package com.kant.llm.eval.agent.redteam.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamAttemptDO;
import org.apache.ibatis.annotations.Mapper;

/** 自动红队攻击尝试 Mapper。 */
@Mapper
public interface AgentRedTeamAttemptMapper extends BaseMapper<AgentRedTeamAttemptDO> {
}
