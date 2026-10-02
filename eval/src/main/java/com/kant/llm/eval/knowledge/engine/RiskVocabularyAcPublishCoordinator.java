package com.kant.llm.eval.knowledge.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kant.llm.common.constant.RiskVocabularyAcRedisKeys;
import com.kant.llm.common.enums.AcSnapshotAckStageEnums;
import com.kant.llm.common.enums.AcSnapshotAckStatusEnums;
import com.kant.llm.common.enums.AcSnapshotPublishStatusEnums;
import com.kant.llm.eval.knowledge.dao.entity.AcSnapshotNodeAckDO;
import com.kant.llm.eval.knowledge.dao.entity.AcSnapshotPublishRecordDO;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeVocabularyKeywordDO;
import com.kant.llm.eval.knowledge.mq.v1.message.RiskVocabularyAcPublishMessage;
import com.kant.llm.eval.knowledge.mq.v1.producer.RiskVocabularyAcMqProducer;
import com.kant.llm.eval.knowledge.service.AcSnapshotDirtyShardService;
import com.kant.llm.eval.knowledge.service.AcSnapshotNodeAckService;
import com.kant.llm.eval.knowledge.service.AcSnapshotPublishRecordService;
import com.kant.llm.eval.knowledge.service.KnowledgeVocabularyKeywordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * AC 快照发布协调器。
 *
 * <p>负责扫描发布记录、等待 BUILD/ACTIVE ACK、切换 Redis latest 指针并完成发布状态收敛。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskVocabularyAcPublishCoordinator {

    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(10);

    private static final Duration ACTIVATE_TIMEOUT = Duration.ofMinutes(5);

    private static final boolean SYNC_STATUS_SYNCED = true;

    private final AcSnapshotPublishRecordService publishRecordService;

    private final AcSnapshotNodeAckService nodeAckService;

    private final RiskVocabularyAcMqProducer acMqProducer;

    private final StringRedisTemplate stringRedisTemplate;

    private final KnowledgeVocabularyKeywordService knowledgeVocabularyKeywordService;

    private final AcSnapshotDirtyShardService dirtyShardService;

//    // @Scheduled(fixedDelay = 5_000L, initialDelay = 10_000L)
    public void coordinate() {
        List<AcSnapshotPublishRecordDO> records = publishRecordService.list(new LambdaQueryWrapper<AcSnapshotPublishRecordDO>()
                .in(AcSnapshotPublishRecordDO::getStatus,
                        AcSnapshotPublishStatusEnums.SNAPSHOT_READY.getCode(),
                        AcSnapshotPublishStatusEnums.BUILDING.getCode(),
                        AcSnapshotPublishStatusEnums.LATEST_SWITCHED.getCode())
                .orderByAsc(AcSnapshotPublishRecordDO::getCreateTime));
        for (AcSnapshotPublishRecordDO record : records) {
            try {
                coordinateRecord(record);
            } catch (Exception ex) {
                markFailed(record, "\u534f\u8c03 AC \u5feb\u7167\u53d1\u5e03\u5931\u8d25\uff1a" + ex.getMessage());
                log.warn("\u534f\u8c03 AC \u5feb\u7167\u53d1\u5e03\u5931\u8d25\uff0cversionId={}", record.getVersionId(), ex);
            }
        }
    }

    private void coordinateRecord(AcSnapshotPublishRecordDO record) {
        if (AcSnapshotPublishStatusEnums.SNAPSHOT_READY.getCode().equals(record.getStatus())) {
            updateStatus(record, AcSnapshotPublishStatusEnums.BUILDING.getCode(), null);
            return;
        }
        if (AcSnapshotPublishStatusEnums.BUILDING.getCode().equals(record.getStatus())) {
            coordinateBuild(record);
            return;
        }
        if (AcSnapshotPublishStatusEnums.LATEST_SWITCHED.getCode().equals(record.getStatus())) {
            coordinateActivation(record);
        }
    }

    private void coordinateBuild(AcSnapshotPublishRecordDO record) {
        if (hasFailedAck(record, AcSnapshotAckStageEnums.BUILD.getCode())) {
            markFailed(record, "\u5b58\u5728\u8282\u70b9\u6784\u5efa AC \u5feb\u7167\u5931\u8d25");
            return;
        }
        if (isTimedOut(record, BUILD_TIMEOUT)) {
            markFailed(record, "\u7b49\u5f85\u8282\u70b9\u6784\u5efa AC \u5feb\u7167\u786e\u8ba4\u8d85\u65f6");
            return;
        }
        int targetNodeCount = safeCount(record.getTargetNodeCount());
        int successCount = countSuccessAck(record, AcSnapshotAckStageEnums.BUILD.getCode());
        updateSuccessCount(record, successCount);
        if (successCount < targetNodeCount) {
            return;
        }

        stringRedisTemplate.opsForValue().set(RiskVocabularyAcRedisKeys.LATEST_VERSION, String.valueOf(record.getVersionId()));
        stringRedisTemplate.opsForValue().set(RiskVocabularyAcRedisKeys.LATEST_HASH, record.getGlobalHash());
        stringRedisTemplate.opsForValue().set(RiskVocabularyAcRedisKeys.LATEST_MANIFEST, record.getManifestKey());
        updateStatus(record, AcSnapshotPublishStatusEnums.LATEST_SWITCHED.getCode(), null);

        acMqProducer.sendActivateMessage(RiskVocabularyAcPublishMessage.builder()
                .messageType(RiskVocabularyAcPublishMessage.ACTIVATE_VERSION)
                .versionId(record.getVersionId())
                .manifestKey(record.getManifestKey())
                .globalHash(record.getGlobalHash())
                .totalWordCount(record.getWordCount())
                .publishTime(LocalDateTime.now())
                .build());
        log.info("AC \u6700\u65b0\u7248\u672c\u5df2\u5207\u6362\uff0c\u6fc0\u6d3b\u5e7f\u64ad\u5df2\u53d1\u9001\uff0cversionId={}", record.getVersionId());
    }

    private void coordinateActivation(AcSnapshotPublishRecordDO record) {
        if (hasFailedAck(record, AcSnapshotAckStageEnums.ACTIVE.getCode())) {
            markFailed(record, "\u5b58\u5728\u8282\u70b9\u6fc0\u6d3b AC \u5feb\u7167\u5931\u8d25");
            return;
        }
        if (isTimedOut(record, ACTIVATE_TIMEOUT)) {
            markFailed(record, "\u7b49\u5f85\u8282\u70b9\u6fc0\u6d3b AC \u5feb\u7167\u786e\u8ba4\u8d85\u65f6");
            return;
        }
        int targetNodeCount = safeCount(record.getTargetNodeCount());
        int successCount = countSuccessAck(record, AcSnapshotAckStageEnums.ACTIVE.getCode());
        updateSuccessCount(record, successCount);
        if (successCount < targetNodeCount) {
            return;
        }
        knowledgeVocabularyKeywordService.update(new LambdaUpdateWrapper<KnowledgeVocabularyKeywordDO>()
                .eq(KnowledgeVocabularyKeywordDO::getDeleted, false)
                .set(KnowledgeVocabularyKeywordDO::getSyncStatus, SYNC_STATUS_SYNCED));
        dirtyShardService.clearAll();
        updateStatus(record, AcSnapshotPublishStatusEnums.ACTIVE.getCode(), null);
        log.info("AC \u5feb\u7167\u53d1\u5e03\u5b8c\u6210\uff0cversionId={}", record.getVersionId());
    }

    private boolean hasFailedAck(AcSnapshotPublishRecordDO record, String stage) {
        if (safeCount(record.getTargetNodeCount()) == 0) {
            return false;
        }
        LambdaQueryWrapper<AcSnapshotNodeAckDO> queryWrapper = buildAckBaseQuery(record, stage)
                .eq(AcSnapshotNodeAckDO::getStatus, AcSnapshotAckStatusEnums.FAILED.getCode());
        return nodeAckService.count(queryWrapper) > 0;
    }

    private int countSuccessAck(AcSnapshotPublishRecordDO record, String stage) {
        if (safeCount(record.getTargetNodeCount()) == 0) {
            return 0;
        }
        LambdaQueryWrapper<AcSnapshotNodeAckDO> queryWrapper = buildAckBaseQuery(record, stage)
                .eq(AcSnapshotNodeAckDO::getStatus, AcSnapshotAckStatusEnums.SUCCESS.getCode())
                .eq(AcSnapshotNodeAckDO::getHash, record.getGlobalHash());
        return Math.toIntExact(nodeAckService.count(queryWrapper));
    }

    private LambdaQueryWrapper<AcSnapshotNodeAckDO> buildAckBaseQuery(AcSnapshotPublishRecordDO record, String stage) {
        LambdaQueryWrapper<AcSnapshotNodeAckDO> queryWrapper = new LambdaQueryWrapper<AcSnapshotNodeAckDO>()
                .eq(AcSnapshotNodeAckDO::getVersionId, record.getVersionId())
                .eq(AcSnapshotNodeAckDO::getStage, stage);
        List<String> targetNodeIds = parseTargetNodeIds(record.getTargetNodeIds());
        if (!targetNodeIds.isEmpty()) {
            queryWrapper.in(AcSnapshotNodeAckDO::getNodeId, targetNodeIds);
        }
        return queryWrapper;
    }

    private void updateStatus(AcSnapshotPublishRecordDO record, String status, String lastError) {
        publishRecordService.update(new LambdaUpdateWrapper<AcSnapshotPublishRecordDO>()
                .eq(AcSnapshotPublishRecordDO::getId, record.getId())
                .set(AcSnapshotPublishRecordDO::getStatus, status)
                .set(AcSnapshotPublishRecordDO::getLastError, lastError));
    }

    private void updateSuccessCount(AcSnapshotPublishRecordDO record, int successCount) {
        publishRecordService.update(new LambdaUpdateWrapper<AcSnapshotPublishRecordDO>()
                .eq(AcSnapshotPublishRecordDO::getId, record.getId())
                .set(AcSnapshotPublishRecordDO::getSuccessNodeCount, successCount));
    }

    private void markFailed(AcSnapshotPublishRecordDO record, String reason) {
        updateStatus(record, AcSnapshotPublishStatusEnums.FAILED.getCode(), trimError(reason));
    }

    private boolean isTimedOut(AcSnapshotPublishRecordDO record, Duration timeout) {
        LocalDateTime createTime = record.getCreateTime();
        return createTime != null && createTime.plus(timeout).isBefore(LocalDateTime.now());
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }

    private List<String> parseTargetNodeIds(String targetNodeIds) {
        if (!StringUtils.hasText(targetNodeIds)) {
            return List.of();
        }
        return Arrays.stream(targetNodeIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String trimError(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }
}
