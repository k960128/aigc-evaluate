package com.kant.llm.eval.es.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kant.llm.eval.es.dao.entity.EvalEsSyncEventDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测 ES 投影同步 outbox 事件 Mapper。
 */
@Mapper
public interface EvalEsSyncEventMapper extends BaseMapper<EvalEsSyncEventDO> {
}
