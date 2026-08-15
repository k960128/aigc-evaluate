package com.kant.llm.eval.basesource.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.basesource.dao.entity.EmbeddingModelInfoDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmbeddingModelInfoMapper extends BaseMapper<EmbeddingModelInfoDO> {
}
