package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.llm.eval.common.config.L2KnowledgeIndexSyncProperties;
import com.kant.llm.eval.common.enums.KbSyncStatusEnums;
import com.kant.llm.eval.dao.entity.KbSyncEventDO;
import com.kant.llm.eval.dao.entity.RiskAttackFeatureDO;
import com.kant.llm.eval.dao.mapper.KbSyncEventMapper;
import com.kant.llm.eval.dao.mapper.RiskAttackFeatureMapper;
import com.kant.llm.eval.search.RiskFeatureEsClient;
import com.kant.llm.eval.service.L2KnowledgeIndexSyncService;
import com.kant.llm.eval.vector.RiskFeatureVectorStoreClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * L2 知识库 ES + PGVector 索引同步服务实现。
 *
 * <p>MySQL 仍是知识库唯一事实源，本服务只负责把变更事件投递到检索索引。
 * 由于历史字段仍沿用 milvus 命名，代码中的 vectorStatus / milvusStatus 实际表示当前 PGVector 同步状态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.l2.index-sync", name = "enabled", havingValue = "true")
public class L2KnowledgeIndexSyncServiceImpl implements L2KnowledgeIndexSyncService {

    private static final String AGGREGATE_ATTACK_FEATURE = "ATTACK_FEATURE";

    private static final String OPERATION_DELETE = "DELETE";

    private final KbSyncEventMapper kbSyncEventMapper;

    private final RiskAttackFeatureMapper riskAttackFeatureMapper;

    private final RiskFeatureEsClient riskFeatureEsClient;

    private final RiskFeatureVectorStoreClient riskFeatureVectorStoreClient;

    private final L2KnowledgeIndexSyncProperties properties;

    /**
     * 定时调度入口。
     */
    @Scheduled(fixedDelayString = "${app.l2.index-sync.fixed-delay-ms:30000}")
    @Transactional(rollbackFor = Exception.class)
    public void syncPendingEventsOnSchedule() {
        log.debug("L2 知识库索引同步定时扫描触发，fixedDelayMs: {}, batchSize: {}",
                properties.getFixedDelayMs(), resolveBatchSize());
        int syncedCount = syncPendingEvents();
        if (syncedCount > 0) {
            log.info("L2 知识库索引同步定时扫描完成，本批处理事件数: {}", syncedCount);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncPendingEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<KbSyncEventDO> events = kbSyncEventMapper.selectList(new LambdaQueryWrapper<KbSyncEventDO>()
                .eq(KbSyncEventDO::getAggregateType, AGGREGATE_ATTACK_FEATURE)
                .and(wrapper -> wrapper
                        .eq(KbSyncEventDO::getEsStatus, KbSyncStatusEnums.PENDING.getCode())
                        .or()
                        .eq(KbSyncEventDO::getMilvusStatus, KbSyncStatusEnums.PENDING.getCode())
                        .or(retryWrapper -> retryWrapper
                                .le(KbSyncEventDO::getNextRetryTime, now)
                                .and(failedWrapper -> failedWrapper
                                        .eq(KbSyncEventDO::getEsStatus, KbSyncStatusEnums.FAILED.getCode())
                                        .or()
                                        .eq(KbSyncEventDO::getMilvusStatus, KbSyncStatusEnums.FAILED.getCode()))))
                .orderByAsc(KbSyncEventDO::getId)
                .last("LIMIT " + resolveBatchSize()));
        if (events.isEmpty()) {
            log.debug("L2 知识库索引同步无待处理事件，batchSize: {}", resolveBatchSize());
            return 0;
        }
        log.info("开始处理 L2 知识库索引同步事件，count: {}, batchSize: {}",
                events.size(), resolveBatchSize());
        for (KbSyncEventDO event : events) {
            syncEvent(event);
        }
        return events.size();
    }

    private void syncEvent(KbSyncEventDO event) {
        RiskAttackFeatureDO feature = riskAttackFeatureMapper.selectById(event.getAggregateId());
        Integer esStatus = event.getEsStatus();
        Integer vectorStatus = event.getMilvusStatus();
        String lastError = null;
        log.debug("开始处理 L2 知识库同步事件，eventId: {}, aggregateId: {}, operationType: {}, esStatus: {}, vectorStatus: {}, retryCount: {}",
                event.getEventId(), event.getAggregateId(), event.getOperationType(),
                esStatus, vectorStatus, event.getRetryCount());

        if (!isSynced(esStatus)) {
            try {
                syncEs(event, feature);
                esStatus = KbSyncStatusEnums.SYNCED.getCode();
                log.debug("L2 知识库 ES 同步成功，eventId: {}, aggregateId: {}",
                        event.getEventId(), event.getAggregateId());
            } catch (Exception ex) {
                esStatus = KbSyncStatusEnums.FAILED.getCode();
                lastError = appendError(lastError, "ES", ex);
                log.warn("L2 知识库 ES 同步失败，eventId: {}, aggregateId: {}, reason: {}",
                        event.getEventId(), event.getAggregateId(), ex.getMessage(), ex);
            }
        }

        if (!isSynced(vectorStatus)) {
            try {
                syncVector(event, feature);
                vectorStatus = KbSyncStatusEnums.SYNCED.getCode();
                log.debug("L2 知识库 PGVector 同步成功，eventId: {}, aggregateId: {}",
                        event.getEventId(), event.getAggregateId());
            } catch (Exception ex) {
                vectorStatus = KbSyncStatusEnums.FAILED.getCode();
                lastError = appendError(lastError, "PGVector", ex);
                log.warn("L2 知识库 PGVector 同步失败，eventId: {}, aggregateId: {}, reason: {}",
                        event.getEventId(), event.getAggregateId(), ex.getMessage(), ex);
            }
        }

        event.setEsStatus(esStatus);
        event.setMilvusStatus(vectorStatus);
        event.setLastError(lastError);
        event.setRetryCount(nextRetryCount(event.getRetryCount(), esStatus, vectorStatus));
        event.setNextRetryTime(needRetry(esStatus, vectorStatus) ? LocalDateTime.now().plusMinutes(1) : null);
        kbSyncEventMapper.updateById(event);
        syncBackAttackFeatureStatus(event, feature);
        log.info("L2 知识库同步事件处理完成，eventId: {}, aggregateId: {}, esStatus: {}, vectorStatus: {}, retryCount: {}, nextRetryTime: {}",
                event.getEventId(), event.getAggregateId(), event.getEsStatus(),
                event.getMilvusStatus(), event.getRetryCount(), event.getNextRetryTime());
    }

    private void syncEs(KbSyncEventDO event, RiskAttackFeatureDO feature) {
        if (shouldDelete(event, feature)) {
            log.debug("L2 知识库 ES 执行删除，eventId: {}, featureId: {}, operationType: {}, featureExists: {}",
                    event.getEventId(), event.getAggregateId(), event.getOperationType(), feature != null);
            riskFeatureEsClient.deleteFeature(event.getAggregateId());
            return;
        }
        if (feature == null) {
            log.debug("L2 知识库 ES 特征不存在，执行兜底删除，eventId: {}, featureId: {}",
                    event.getEventId(), event.getAggregateId());
            riskFeatureEsClient.deleteFeature(event.getAggregateId());
            return;
        }
        log.debug("L2 知识库 ES 执行写入，eventId: {}, featureId: {}, riskDetailsId: {}, contentHash: {}",
                event.getEventId(), feature.getId(), feature.getRiskDetailsId(), feature.getContentHash());
        riskFeatureEsClient.upsertFeature(feature);
    }

    private void syncVector(KbSyncEventDO event, RiskAttackFeatureDO feature) {
        if (shouldDelete(event, feature)) {
            log.debug("L2 知识库 PGVector 执行删除，eventId: {}, featureId: {}, operationType: {}, featureExists: {}",
                    event.getEventId(), event.getAggregateId(), event.getOperationType(), feature != null);
            riskFeatureVectorStoreClient.deleteFeature(event.getAggregateId());
            return;
        }
        if (feature == null) {
            log.debug("L2 知识库 PGVector 特征不存在，执行兜底删除，eventId: {}, featureId: {}",
                    event.getEventId(), event.getAggregateId());
            riskFeatureVectorStoreClient.deleteFeature(event.getAggregateId());
            return;
        }
        log.debug("L2 知识库 PGVector 执行写入，eventId: {}, featureId: {}, riskDetailsId: {}, contentHash: {}",
                event.getEventId(), feature.getId(), feature.getRiskDetailsId(), feature.getContentHash());
        riskFeatureVectorStoreClient.upsertFeature(feature);
    }

    private boolean shouldDelete(KbSyncEventDO event, RiskAttackFeatureDO feature) {
        return OPERATION_DELETE.equals(event.getOperationType())
                || feature == null
                || !Objects.equals(feature.getStatus(), 1);
    }

    private void syncBackAttackFeatureStatus(KbSyncEventDO event, RiskAttackFeatureDO feature) {
        if (feature == null) {
            return;
        }
        feature.setEsSyncStatus(event.getEsStatus());
        feature.setMilvusSyncStatus(event.getMilvusStatus());
        if (Objects.equals(event.getEsStatus(), KbSyncStatusEnums.SYNCED.getCode())
                && Objects.equals(event.getMilvusStatus(), KbSyncStatusEnums.SYNCED.getCode())) {
            feature.setSyncStatus(KbSyncStatusEnums.SYNCED.getCode());
        } else if (Objects.equals(event.getEsStatus(), KbSyncStatusEnums.FAILED.getCode())
                || Objects.equals(event.getMilvusStatus(), KbSyncStatusEnums.FAILED.getCode())) {
            feature.setSyncStatus(KbSyncStatusEnums.FAILED.getCode());
        } else {
            feature.setSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        }
        riskAttackFeatureMapper.updateById(feature);
        log.debug("L2 知识库特征同步状态已回写，featureId: {}, syncStatus: {}, esStatus: {}, vectorStatus: {}",
                feature.getId(), feature.getSyncStatus(), feature.getEsSyncStatus(), feature.getMilvusSyncStatus());
    }

    private Integer nextRetryCount(Integer retryCount, Integer esStatus, Integer vectorStatus) {
        if (!needRetry(esStatus, vectorStatus)) {
            return retryCount == null ? 0 : retryCount;
        }
        return retryCount == null ? 1 : retryCount + 1;
    }

    private boolean needRetry(Integer esStatus, Integer vectorStatus) {
        return Objects.equals(esStatus, KbSyncStatusEnums.FAILED.getCode())
                || Objects.equals(vectorStatus, KbSyncStatusEnums.FAILED.getCode());
    }

    private boolean isSynced(Integer status) {
        return Objects.equals(status, KbSyncStatusEnums.SYNCED.getCode());
    }

    private String appendError(String currentError, String target, Exception ex) {
        String error = target + ": " + ex.getMessage();
        return currentError == null ? error : currentError + "; " + error;
    }

    private int resolveBatchSize() {
        return properties.getBatchSize() <= 0 ? 50 : properties.getBatchSize();
    }
}
