package com.kant.llm.eval.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * L2 攻击特征知识 Mapper。
 */
@Mapper
public interface KnowledgeFeatureDocumentMapper extends BaseMapper<KnowledgeFeatureDocumentDO> {
}
