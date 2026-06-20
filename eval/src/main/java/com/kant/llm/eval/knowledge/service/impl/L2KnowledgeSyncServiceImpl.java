package com.kant.llm.eval.knowledge.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kant.llm.common.constant.EsDocumentChunk;
import com.kant.llm.common.enums.KbSyncStatusEnums;
import com.kant.framework.exception.ServiceException;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeAttackFeatureDO;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeSyncEventDO;
import com.kant.llm.eval.knowledge.dto.resp.L2KnowledgeSyncSubmitVO;
import com.kant.llm.eval.pipeline.mq.message.KbSyncEventMessage;
import com.kant.llm.eval.pipeline.mq.message.L2KnowledgeSyncBatchMessage;
import com.kant.llm.eval.pipeline.mq.producer.KbSyncMqProducer;
import com.kant.llm.eval.knowledge.service.KnowledgeSyncEventService;
import com.kant.llm.eval.knowledge.service.L2KnowledgeSyncService;
import com.kant.llm.eval.knowledge.service.KnowledgeAttackFeatureService;
import com.kant.llm.eval.pipeline.service.es.ElasticSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * L2 知识库索引同步服务实现。
 */
@Slf4j
@Service
public class L2KnowledgeSyncServiceImpl implements L2KnowledgeSyncService {

    private static final int DISPATCH_BATCH_SIZE = 500;

    private static final int ENABLED_STATUS = 1;

    private static final String TRIGGER_TYPE_ONE_CLICK = "ONE_CLICK";

    private static final String ACCEPTED_STATUS = "ACCEPTED";

    private static final String AGGREGATE_TYPE_ATTACK_FEATURE = "ATTACK_FEATURE";

    private static final String OPERATION_REINDEX = "REINDEX";

    private static final String OPERATION_DELETE = "DELETE";

    private static final DateTimeFormatter ES_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KnowledgeAttackFeatureService knowledgeAttackFeatureService;

    private final KnowledgeSyncEventService knowledgeSyncEventService;

    private final KbSyncMqProducer kbSyncMqProducer;

    private final ElasticSearchService elasticSearchService;

    private final VectorStore vectorStore;

    private final TransactionTemplate transactionTemplate;

    public L2KnowledgeSyncServiceImpl(KnowledgeAttackFeatureService knowledgeAttackFeatureService,
                                      KnowledgeSyncEventService knowledgeSyncEventService,
                                      KbSyncMqProducer kbSyncMqProducer,
                                      ElasticSearchService elasticSearchService,
                                      @Qualifier("vectorStore") VectorStore vectorStore,
                                      TransactionTemplate transactionTemplate) {
        this.knowledgeAttackFeatureService = knowledgeAttackFeatureService;
        this.knowledgeSyncEventService = knowledgeSyncEventService;
        this.kbSyncMqProducer = kbSyncMqProducer;
        this.elasticSearchService = elasticSearchService;
        this.vectorStore = vectorStore;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public L2KnowledgeSyncSubmitVO submitPendingSync() {
        String batchId = "l2-kb-sync-" + IdUtil.fastSimpleUUID();
        kbSyncMqProducer.sendBatchSyncMessage(L2KnowledgeSyncBatchMessage.builder()
                .batchId(batchId)
                .triggerType(TRIGGER_TYPE_ONE_CLICK)
                .triggerTime(LocalDateTime.now())
                .build());
        return L2KnowledgeSyncSubmitVO.builder()
                .batchId(batchId)
                .status(ACCEPTED_STATUS)
                .message("L2 知识库一键同步任务已提交，后续由 RocketMQ 异步写入 ES 和 PG 向量库")
                .build();
    }

    @Override
    public void dispatchPendingSync(L2KnowledgeSyncBatchMessage message) {
        String batchId = message == null ? null : message.getBatchId();
        log.info("开始拆分 L2 知识库同步批次，batchId: {}", batchId);
        long lastId = 0L;
        int dispatchedCount = 0;
        while (true) {
            List<KnowledgeAttackFeatureDO> features = knowledgeAttackFeatureService.list(pendingFeatureQuery(lastId));
            if (CollectionUtil.isEmpty(features)) {
                break;
            }
            for (KnowledgeAttackFeatureDO feature : features) {
                lastId = Math.max(lastId, nullToZero(feature.getId()));
                KnowledgeSyncEventDO syncEvent = saveOrLoadSyncEvent(feature);
                kbSyncMqProducer.sendEventSyncMessage(KbSyncEventMessage.builder()
                        .batchId(batchId)
                        .eventId(syncEvent.getEventId())
                        .build());
                dispatchedCount++;
            }
            if (features.size() < DISPATCH_BATCH_SIZE) {
                break;
            }
        }
        log.info("L2 知识库同步批次拆分完成，batchId: {}, dispatchedCount: {}", batchId, dispatchedCount);
    }

    @Override
    public void syncEvent(KbSyncEventMessage message) {
        if (message == null || !StringUtils.hasText(message.getEventId())) {
            log.warn("L2 知识库同步事件消息为空，跳过消费，message: {}", message);
            return;
        }
        KnowledgeSyncEventDO syncEvent = loadSyncEvent(message.getEventId());
        if (syncEvent == null) {
            log.warn("L2 知识库同步事件不存在，跳过消费，eventId: {}", message.getEventId());
            return;
        }
        if (isEventFinished(syncEvent)) {
            log.info("L2 知识库同步事件已完成，跳过重复消费，eventId: {}", syncEvent.getEventId());
            return;
        }

        KnowledgeAttackFeatureDO feature = knowledgeAttackFeatureService.getById(syncEvent.getAggregateId());
        if (isStaleEvent(syncEvent, feature)) {
            markEventSkipped(syncEvent, "同步事件已落后于当前知识状态，跳过处理");
            log.info("L2 知识库同步事件已过期并跳过，eventId: {}, aggregateId: {}",
                    syncEvent.getEventId(), syncEvent.getAggregateId());
            return;
        }

        Integer esStatus = normalizeTargetStatus(syncEvent.getEsStatus());
        Integer pgStatus = normalizeTargetStatus(syncEvent.getPgStatus());
        String lastError = null;
        boolean failed = false;

        if (!KbSyncStatusEnums.SYNCED.getCode().equals(esStatus)) {
            try {
                syncEs(syncEvent, feature);
                esStatus = KbSyncStatusEnums.SYNCED.getCode();
            } catch (Exception ex) {
                esStatus = KbSyncStatusEnums.FAILED.getCode();
                lastError = appendError(lastError, "ES", ex);
                failed = true;
            }
        }

        if (!KbSyncStatusEnums.SYNCED.getCode().equals(pgStatus)) {
            try {
                syncPgVector(syncEvent, feature);
                pgStatus = KbSyncStatusEnums.SYNCED.getCode();
            } catch (Exception ex) {
                pgStatus = KbSyncStatusEnums.FAILED.getCode();
                lastError = appendError(lastError, "PGVector", ex);
                failed = true;
            }
        }

        Integer finalEsStatus = esStatus;
        Integer finalPgStatus = pgStatus;
        String finalLastError = lastError;
        boolean finalFailed = failed;
        transactionTemplate.executeWithoutResult(status -> {
            updateEventResult(syncEvent, finalEsStatus, finalPgStatus, finalLastError, finalFailed);
            updateFeatureSyncResult(syncEvent, feature, finalEsStatus, finalPgStatus, finalFailed);
        });

        if (failed) {
            throw new ServiceException("L2 知识库同步事件处理失败，eventId=" + syncEvent.getEventId()
                    + "，原因：" + lastError);
        }
        log.info("L2 知识库同步事件处理完成，batchId: {}, eventId: {}, aggregateId: {}",
                message.getBatchId(), syncEvent.getEventId(), syncEvent.getAggregateId());
    }

    private LambdaQueryWrapper<KnowledgeAttackFeatureDO> pendingFeatureQuery(long lastId) {
        return new LambdaQueryWrapper<KnowledgeAttackFeatureDO>()
                .gt(KnowledgeAttackFeatureDO::getId, lastId)
                .and(wrapper -> wrapper
                        .ne(KnowledgeAttackFeatureDO::getSyncStatus, KbSyncStatusEnums.SYNCED.getCode())
                        .or()
                        .ne(KnowledgeAttackFeatureDO::getEsSyncStatus, KbSyncStatusEnums.SYNCED.getCode())
                        .or()
                        .ne(KnowledgeAttackFeatureDO::getPgSyncStatus, KbSyncStatusEnums.SYNCED.getCode()))
                .orderByAsc(KnowledgeAttackFeatureDO::getId)
                .last("LIMIT " + DISPATCH_BATCH_SIZE);
    }

    private KnowledgeSyncEventDO saveOrLoadSyncEvent(KnowledgeAttackFeatureDO feature) {
        KnowledgeSyncEventDO syncEvent = buildSyncEvent(feature);
        try {
            knowledgeSyncEventService.save(syncEvent);
            return syncEvent;
        } catch (DuplicateKeyException ex) {
            KnowledgeSyncEventDO existingEvent = loadSyncEvent(syncEvent.getEventId());
            if (existingEvent == null) {
                throw ex;
            }
            return existingEvent;
        }
    }

    private KnowledgeSyncEventDO buildSyncEvent(KnowledgeAttackFeatureDO feature) {
        String operationType = resolveOperationType(feature);
        KnowledgeSyncEventDO event = new KnowledgeSyncEventDO();
        event.setEventId(buildEventId(feature, operationType));
        event.setAggregateType(AGGREGATE_TYPE_ATTACK_FEATURE);
        event.setAggregateId(feature.getId());
        event.setOperationType(operationType);
        event.setRiskDetailsId(feature.getRiskDetailsId());
        event.setContentHash(feature.getContentHash());
        event.setVersion(feature.getVersion() == null ? 1 : feature.getVersion());
        event.setPayload(JSON.toJSONString(buildFeaturePayload(feature, operationType)));
        event.setEsStatus(KbSyncStatusEnums.PENDING.getCode());
        event.setMilvusStatus(KbSyncStatusEnums.SYNCED.getCode());
        event.setPgStatus(KbSyncStatusEnums.PENDING.getCode());
        event.setRetryCount(0);
        return event;
    }

    private Map<String, Object> buildFeaturePayload(KnowledgeAttackFeatureDO feature, String operationType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfNotNull(payload, "operationType", operationType);
        putIfNotNull(payload, "featureId", feature.getId());
        putIfNotNull(payload, "riskDetailsId", feature.getRiskDetailsId());
        putIfNotNull(payload, "categoryId", feature.getCategoryId());
        putIfNotNull(payload, "featureCode", feature.getFeatureCode());
        putIfNotNull(payload, "featureText", feature.getFeatureText());
        putIfNotNull(payload, "normalizedText", feature.getNormalizedText());
        putIfNotNull(payload, "featureType", feature.getFeatureType());
        putIfNotNull(payload, "polarity", feature.getPolarity());
        putIfNotNull(payload, "riskLevel", feature.getRiskLevel());
        putIfNotNull(payload, "language", feature.getLanguage());
        putIfNotNull(payload, "tags", feature.getTags());
        putIfNotNull(payload, "source", feature.getSource());
        putIfNotNull(payload, "weight", feature.getWeight() == null ? null : feature.getWeight().toPlainString());
        putIfNotNull(payload, "contentHash", feature.getContentHash());
        putIfNotNull(payload, "version", feature.getVersion());
        putIfNotNull(payload, "status", feature.getStatus());
        return payload;
    }

    private String resolveOperationType(KnowledgeAttackFeatureDO feature) {
        if (KbSyncStatusEnums.DELETE_PENDING.getCode().equals(feature.getSyncStatus())
                || !Objects.equals(feature.getStatus(), ENABLED_STATUS)) {
            return OPERATION_DELETE;
        }
        return OPERATION_REINDEX;
    }

    private String buildEventId(KnowledgeAttackFeatureDO feature, String operationType) {
        String hash = StringUtils.hasText(feature.getContentHash()) ? feature.getContentHash() : "nohash";
        String hashPart = hash.length() > 12 ? hash.substring(0, 12) : hash;
        Integer version = feature.getVersion() == null ? 1 : feature.getVersion();
        return ("l2kb-" + operationType.toLowerCase(Locale.ROOT) + "-" + feature.getId()
                + "-" + version + "-" + hashPart).toLowerCase(Locale.ROOT);
    }

    private KnowledgeSyncEventDO loadSyncEvent(String eventId) {
        return knowledgeSyncEventService.getOne(new LambdaQueryWrapper<KnowledgeSyncEventDO>()
                .eq(KnowledgeSyncEventDO::getEventId, eventId)
                .last("LIMIT 1"));
    }

    private boolean isEventFinished(KnowledgeSyncEventDO syncEvent) {
        return KbSyncStatusEnums.SYNCED.getCode().equals(syncEvent.getEsStatus())
                && KbSyncStatusEnums.SYNCED.getCode().equals(syncEvent.getPgStatus());
    }

    private boolean isStaleEvent(KnowledgeSyncEventDO syncEvent, KnowledgeAttackFeatureDO feature) {
        if (OPERATION_DELETE.equals(syncEvent.getOperationType())) {
            return feature != null
                    && Objects.equals(feature.getStatus(), ENABLED_STATUS)
                    && !KbSyncStatusEnums.DELETE_PENDING.getCode().equals(feature.getSyncStatus());
        }
        if (feature == null) {
            return true;
        }
        if (!Objects.equals(feature.getStatus(), ENABLED_STATUS)) {
            return true;
        }
        if (syncEvent.getVersion() != null && feature.getVersion() != null
                && !Objects.equals(syncEvent.getVersion(), feature.getVersion())) {
            return true;
        }
        return StringUtils.hasText(syncEvent.getContentHash())
                && StringUtils.hasText(feature.getContentHash())
                && !Objects.equals(syncEvent.getContentHash(), feature.getContentHash());
    }

    private void markEventSkipped(KnowledgeSyncEventDO syncEvent, String reason) {
        transactionTemplate.executeWithoutResult(status -> {
            KnowledgeSyncEventDO updateEvent = new KnowledgeSyncEventDO();
            updateEvent.setId(syncEvent.getId());
            updateEvent.setEsStatus(KbSyncStatusEnums.SYNCED.getCode());
            updateEvent.setMilvusStatus(KbSyncStatusEnums.SYNCED.getCode());
            updateEvent.setPgStatus(KbSyncStatusEnums.SYNCED.getCode());
            updateEvent.setNextRetryTime(null);
            updateEvent.setLastError(reason);
            knowledgeSyncEventService.updateById(updateEvent);
        });
    }

    private void syncEs(KnowledgeSyncEventDO syncEvent, KnowledgeAttackFeatureDO feature) throws Exception {
        if (OPERATION_DELETE.equals(syncEvent.getOperationType())) {
            elasticSearchService.deleteById(String.valueOf(syncEvent.getAggregateId()));
            return;
        }
        elasticSearchService.indexSingle(buildEsDocument(feature));
    }

    private void syncPgVector(KnowledgeSyncEventDO syncEvent, KnowledgeAttackFeatureDO feature) {
        String documentId = buildVectorDocumentId(syncEvent.getAggregateId());
        if (OPERATION_DELETE.equals(syncEvent.getOperationType())) {
            vectorStore.delete(List.of(documentId));
            return;
        }
        vectorStore.add(List.of(buildVectorDocument(feature, documentId)));
    }

    private EsDocumentChunk buildEsDocument(KnowledgeAttackFeatureDO feature) {
        EsDocumentChunk doc = new EsDocumentChunk();
        doc.setId(String.valueOf(feature.getId()));
        doc.setFeatureId(feature.getId());
        doc.setRiskDetailsId(feature.getRiskDetailsId());
        doc.setCategoryId(feature.getCategoryId());
        doc.setFeatureCode(feature.getFeatureCode());
        doc.setFeatureText(feature.getFeatureText());
        doc.setNormalizedText(feature.getNormalizedText());
        doc.setFeatureType(feature.getFeatureType());
        doc.setPolarity(feature.getPolarity());
        doc.setRiskLevel(feature.getRiskLevel());
        doc.setLanguage(feature.getLanguage());
        doc.setTags(feature.getTags());
        doc.setSource(feature.getSource());
        doc.setWeight(feature.getWeight());
        doc.setContentHash(feature.getContentHash());
        doc.setVersion(feature.getVersion());
        doc.setStatus(feature.getStatus());
        doc.setCreateTime(formatDateTime(feature.getCreateTime()));
        doc.setUpdateTime(formatDateTime(feature.getUpdateTime()));
        doc.setContent(resolveFeatureText(feature));
        doc.setMetadata(buildVectorMetadata(feature));
        return doc;
    }

    private Document buildVectorDocument(KnowledgeAttackFeatureDO feature, String documentId) {
        return new Document(documentId, resolveFeatureText(feature), buildVectorMetadata(feature));
    }

    private Map<String, Object> buildVectorMetadata(KnowledgeAttackFeatureDO feature) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfNotNull(metadata, "featureId", feature.getId());
        putIfNotNull(metadata, "riskDetailsId", feature.getRiskDetailsId());
        putIfNotNull(metadata, "categoryId", feature.getCategoryId());
        putIfNotNull(metadata, "featureCode", feature.getFeatureCode());
        putIfNotNull(metadata, "featureType", feature.getFeatureType());
        putIfNotNull(metadata, "polarity", feature.getPolarity());
        putIfNotNull(metadata, "riskLevel", feature.getRiskLevel());
        putIfNotNull(metadata, "language", feature.getLanguage());
        putIfNotNull(metadata, "source", feature.getSource());
        putIfNotNull(metadata, "contentHash", feature.getContentHash());
        putIfNotNull(metadata, "version", feature.getVersion());
        putIfNotNull(metadata, "status", feature.getStatus());
        putIfNotNull(metadata, "weight", toPlainString(feature.getWeight()));
        return metadata;
    }

    private String resolveFeatureText(KnowledgeAttackFeatureDO feature) {
        if (StringUtils.hasText(feature.getNormalizedText())) {
            return feature.getNormalizedText();
        }
        if (StringUtils.hasText(feature.getFeatureText())) {
            return feature.getFeatureText();
        }
        throw new ServiceException("L2 知识库特征文本为空，featureId=" + feature.getId());
    }

    private String buildVectorDocumentId(Long featureId) {
        return UUID.nameUUIDFromBytes(("l2-risk-attack-feature:" + featureId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(ES_DATE_TIME_FORMATTER);
    }

    private void updateEventResult(KnowledgeSyncEventDO syncEvent,
                                   Integer esStatus,
                                   Integer pgStatus,
                                   String lastError,
                                   boolean failed) {
        KnowledgeSyncEventDO updateEvent = new KnowledgeSyncEventDO();
        updateEvent.setId(syncEvent.getId());
        updateEvent.setEsStatus(esStatus);
        updateEvent.setMilvusStatus(KbSyncStatusEnums.SYNCED.getCode());
        updateEvent.setPgStatus(pgStatus);
        updateEvent.setLastError(lastError);
        updateEvent.setNextRetryTime(failed ? LocalDateTime.now().plusMinutes(5) : null);
        updateEvent.setRetryCount(failed ? nullToZero(syncEvent.getRetryCount()) + 1 : nullToZero(syncEvent.getRetryCount()));
        knowledgeSyncEventService.updateById(updateEvent);
    }

    private void updateFeatureSyncResult(KnowledgeSyncEventDO syncEvent,
                                         KnowledgeAttackFeatureDO feature,
                                         Integer esStatus,
                                         Integer pgStatus,
                                         boolean failed) {
        if (feature == null) {
            return;
        }
        Integer syncStatus = failed ? KbSyncStatusEnums.FAILED.getCode() : KbSyncStatusEnums.SYNCED.getCode();
        LambdaUpdateWrapper<KnowledgeAttackFeatureDO> updateWrapper = new LambdaUpdateWrapper<KnowledgeAttackFeatureDO>()
                .eq(KnowledgeAttackFeatureDO::getId, feature.getId())
                .eq(feature.getVersion() != null && syncEvent.getVersion() != null,
                        KnowledgeAttackFeatureDO::getVersion, syncEvent.getVersion())
                .eq(StringUtils.hasText(feature.getContentHash()) && StringUtils.hasText(syncEvent.getContentHash()),
                        KnowledgeAttackFeatureDO::getContentHash, syncEvent.getContentHash())
                .set(KnowledgeAttackFeatureDO::getSyncStatus, syncStatus)
                .set(KnowledgeAttackFeatureDO::getEsSyncStatus, esStatus)
                .set(KnowledgeAttackFeatureDO::getMilvusSyncStatus, KbSyncStatusEnums.SYNCED.getCode())
                .set(KnowledgeAttackFeatureDO::getPgSyncStatus, pgStatus);
        knowledgeAttackFeatureService.update(updateWrapper);
    }

    private Integer normalizeTargetStatus(Integer status) {
        return status == null ? KbSyncStatusEnums.PENDING.getCode() : status;
    }

    private String appendError(String lastError, String target, Exception ex) {
        String current = target + "同步失败：" + (StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName());
        if (!StringUtils.hasText(lastError)) {
            return trimError(current);
        }
        return trimError(lastError + "；" + current);
    }

    private String trimError(String message) {
        if (message == null || message.length() <= 1000) {
            return message;
        }
        return message.substring(0, 1000);
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private String toPlainString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
