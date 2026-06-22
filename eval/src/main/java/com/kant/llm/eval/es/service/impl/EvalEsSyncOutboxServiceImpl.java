package com.kant.llm.eval.es.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import com.kant.llm.common.enums.EvalEsSyncAggregateTypeEnums;
import com.kant.llm.common.enums.EvalEsSyncOperationTypeEnums;
import com.kant.llm.common.enums.EvalEsSyncStatusEnums;
import com.kant.llm.eval.es.dao.entity.EvalEsSyncEventDO;
import com.kant.llm.eval.es.dao.mapper.EvalEsSyncEventMapper;
import com.kant.llm.eval.es.service.EvalEsSyncOutboxService;
import com.kant.llm.eval.pipeline.dao.entity.EvalPipelineNodeDetailDO;
import com.kant.llm.eval.pipeline.dao.mapper.EvalPipelineNodeDetailMapper;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.task.dao.mapper.EvalResultDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 评测 ES 投影 outbox 记录服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalEsSyncOutboxServiceImpl implements EvalEsSyncOutboxService {

    private final EvalEsSyncEventMapper evalEsSyncEventMapper;
    private final EvalResultDetailMapper evalResultDetailMapper;
    private final EvalPipelineNodeDetailMapper evalPipelineNodeDetailMapper;

    @Override
    public void recordResultDetailUpsert(Long resultDetailId) {
        if (resultDetailId == null) {
            return;
        }
        EvalResultDetailDO resultDetail = evalResultDetailMapper.selectById(resultDetailId);
        if (resultDetail == null) {
            log.warn("记录评测结果 ES 同步事件失败，结果明细不存在，resultDetailId: {}", resultDetailId);
            return;
        }
        EvalEsSyncEventDO event = buildBaseEvent(
                EvalEsSyncAggregateTypeEnums.RESULT_DETAIL,
                resultDetail.getId(),
                resultDetail.getTaskId(),
                resultDetail.getTaskDetailId(),
                resultDetail.getId(),
                resultDetail.getSampleId());
        evalEsSyncEventMapper.insert(event);
        log.info("评测结果 ES 同步事件已记录，eventId: {}, resultDetailId: {}",
                event.getEventId(), resultDetailId);
    }

    @Override
    public void recordResultDetailUpsertBatch(Collection<Long> resultDetailIds) {
        if (CollectionUtil.isEmpty(resultDetailIds)) {
            return;
        }
        resultDetailIds.stream()
                .filter(id -> id != null)
                .distinct()
                .forEach(this::recordResultDetailUpsert);
    }

    @Override
    public void recordPipelineNodeUpsert(Long nodeRecordId) {
        if (nodeRecordId == null) {
            return;
        }
        EvalPipelineNodeDetailDO nodeDetail = evalPipelineNodeDetailMapper.selectById(nodeRecordId);
        if (nodeDetail == null) {
            log.warn("记录流水线节点 ES 同步事件失败，节点日志不存在，nodeRecordId: {}", nodeRecordId);
            return;
        }
        EvalEsSyncEventDO event = buildBaseEvent(
                EvalEsSyncAggregateTypeEnums.PIPELINE_NODE,
                nodeDetail.getId(),
                nodeDetail.getTaskId(),
                nodeDetail.getTaskDetailId(),
                nodeDetail.getResultDetailId(),
                nodeDetail.getSampleId());
        evalEsSyncEventMapper.insert(event);
        log.info("流水线节点 ES 同步事件已记录，eventId: {}, nodeRecordId: {}, nodeCode: {}",
                event.getEventId(), nodeRecordId, nodeDetail.getNodeCode());
    }

    private EvalEsSyncEventDO buildBaseEvent(EvalEsSyncAggregateTypeEnums aggregateType,
                                             Long aggregateId,
                                             Long taskId,
                                             Long taskDetailId,
                                             Long resultDetailId,
                                             Long sampleId) {
        return EvalEsSyncEventDO.builder()
                .eventId(String.valueOf(IdUtil.getSnowflake().nextId()))
                .aggregateType(aggregateType.getCode())
                .aggregateId(aggregateId)
                .operationType(EvalEsSyncOperationTypeEnums.UPSERT.getCode())
                .taskId(taskId)
                .taskDetailId(taskDetailId)
                .resultDetailId(resultDetailId)
                .sampleId(sampleId)
                .syncStatus(EvalEsSyncStatusEnums.PENDING.getCode())
                .retryCount(0)
                .nextRetryTime(LocalDateTime.now())
                .build();
    }
}
