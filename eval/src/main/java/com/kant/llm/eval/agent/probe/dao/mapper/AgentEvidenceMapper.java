package com.kant.llm.eval.agent.probe.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.probe.dao.entity.AgentEvidenceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动态评测证据 Mapper。
 */
@Mapper
public interface AgentEvidenceMapper extends BaseMapper<AgentEvidenceDO> {
}
