package com.kant.llm.eval.agent.redteam.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamCommandDO;
import org.apache.ibatis.annotations.Mapper;
/** D03控制命令持久化入口。 */
@Mapper
public interface AgentRedTeamCommandMapper extends BaseMapper<AgentRedTeamCommandDO> {}
