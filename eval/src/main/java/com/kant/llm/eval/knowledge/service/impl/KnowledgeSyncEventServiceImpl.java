package com.kant.llm.eval.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeSyncEventDO;
import com.kant.llm.eval.knowledge.dao.mapper.KnowledgeSyncEventMapper;
import com.kant.llm.eval.knowledge.service.KnowledgeSyncEventService;
import org.springframework.stereotype.Service;

/**
 * 知识库索引同步事件服务实现。
 */
@Service
public class KnowledgeSyncEventServiceImpl extends ServiceImpl<KnowledgeSyncEventMapper, KnowledgeSyncEventDO> implements KnowledgeSyncEventService {
}
