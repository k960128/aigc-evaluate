package com.kant.llm.eval.dataset.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.dataset.dao.entity.DataSetDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataSetMapper extends BaseMapper<DataSetDO> {
}
