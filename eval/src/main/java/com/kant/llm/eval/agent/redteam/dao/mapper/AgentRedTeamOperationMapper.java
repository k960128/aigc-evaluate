package com.kant.llm.eval.agent.redteam.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.redteam.dao.entity.AgentRedTeamOperationDO;
import org.apache.ibatis.annotations.Mapper;
/** 受限操作日志Mapper。 */
@Mapper public interface AgentRedTeamOperationMapper extends BaseMapper<AgentRedTeamOperationDO> {}
