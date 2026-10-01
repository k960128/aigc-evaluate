package com.kant.llm.eval.agent.finding.dao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.agent.finding.dao.entity.AgentFindingReviewDO;
import org.apache.ibatis.annotations.Mapper;
/** 追加式复核记录Mapper。 */
@Mapper
public interface AgentFindingReviewMapper extends BaseMapper<AgentFindingReviewDO> {}
