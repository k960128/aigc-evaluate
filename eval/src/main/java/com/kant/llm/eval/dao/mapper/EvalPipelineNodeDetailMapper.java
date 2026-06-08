package com.kant.llm.eval.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.dao.entity.EvalPipelineNodeDetailDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测流水线节点执行明细 Mapper。
 */
@Mapper
public interface EvalPipelineNodeDetailMapper extends BaseMapper<EvalPipelineNodeDetailDO> {
}
