package com.kant.llm.eval.knowledge.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeSyncEventDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库索引同步事件 Mapper。
 */
@Mapper
public interface KnowledgeSyncEventMapper extends BaseMapper<KnowledgeSyncEventDO> {
}
