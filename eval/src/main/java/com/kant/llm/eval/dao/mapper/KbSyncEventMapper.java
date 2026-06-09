package com.kant.llm.eval.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.dao.entity.KbSyncEventDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库索引同步事件 Mapper。
 */
@Mapper
public interface KbSyncEventMapper extends BaseMapper<KbSyncEventDO> {
}
