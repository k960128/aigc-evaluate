package com.kant.llm.eval.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.dao.entity.EvalTaskDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EvalTaskMapper extends BaseMapper<EvalTaskDO> {
}
