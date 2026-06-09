package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.KbSyncEventDO;
import com.kant.llm.eval.dao.mapper.KbSyncEventMapper;
import com.kant.llm.eval.service.KbSyncEventService;
import org.springframework.stereotype.Service;

/**
 * 知识库索引同步事件服务实现。
 */
@Service
public class KbSyncEventServiceImpl extends ServiceImpl<KbSyncEventMapper, KbSyncEventDO> implements KbSyncEventService {
}
