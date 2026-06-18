package com.kant.llm.eval.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.framework.errorcode.BaseErrorCode;
import com.kant.framework.exception.ServiceException;
import com.kant.llm.eval.common.constant.RiskVocabularyAcRedisKeys;
import com.kant.llm.eval.common.enums.AcSnapshotPublishStatusEnums;
import com.kant.llm.eval.dao.entity.AcSnapshotNodeRegistryDO;
import com.kant.llm.eval.dao.entity.AcSnapshotPublishRecordDO;
import com.kant.llm.eval.dao.entity.RiskDetailsDO;
import com.kant.llm.eval.dao.entity.RiskVocabularyKeywordDO;
import com.kant.llm.eval.dao.mapper.RiskVocabularyKeywordMapper;
import com.kant.llm.eval.engine.model.RiskVocabularySnapshotItem;
import com.kant.llm.eval.engine.model.RiskVocabularySnapshotManifest;
import com.kant.llm.eval.engine.model.RiskVocabularySnapshotShard;
import com.kant.llm.eval.engine.model.RiskVocabularySnapshotShardMeta;
import com.kant.llm.eval.engine.support.RiskVocabularyAcSupport;
import com.kant.llm.eval.mq.message.RiskVocabularyAcPublishMessage;
import com.kant.llm.eval.mq.producer.RiskVocabularyAcMqProducer;
import com.kant.llm.eval.service.AcSnapshotDirtyShardService;
import com.kant.llm.eval.service.AcSnapshotNodeRegistryService;
import com.kant.llm.eval.service.AcSnapshotPublishRecordService;
import com.kant.llm.eval.service.RiskDetailsService;
import com.kant.llm.eval.service.RiskVocabularyKeywordService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 风险词库特征词服务实现。
 */
@Slf4j
@Service
public class RiskVocabularyKeywordServiceImpl
        extends ServiceImpl<RiskVocabularyKeywordMapper, RiskVocabularyKeywordDO>
        implements RiskVocabularyKeywordService {

    private static final boolean SYNC_STATUS_SYNCED = true;

    private static final int PAGE_SIZE = 1_000;

    private static final int SHARD_BUCKET_SIZE = 20_000;

    private static final int HEARTBEAT_TIMEOUT_SECONDS = 60;

    private static final String PARTITION_TYPE_RISK_DETAILS = "RISK_DETAILS";

    private static final List<String> UNFINISHED_PUBLISH_STATUSES = List.of(
            AcSnapshotPublishStatusEnums.SNAPSHOT_READY.getCode(),
            AcSnapshotPublishStatusEnums.BUILDING.getCode(),
            AcSnapshotPublishStatusEnums.LATEST_SWITCHED.getCode()
    );

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    private final RedissonClient redissonClient;

    private final RiskDetailsService riskDetailsService;

    private final AcSnapshotPublishRecordService publishRecordService;

    private final AcSnapshotNodeRegistryService nodeRegistryService;

    private final AcSnapshotDirtyShardService dirtyShardService;

    private final RiskVocabularyAcMqProducer acMqProducer;

    public RiskVocabularyKeywordServiceImpl(StringRedisTemplate stringRedisTemplate,
                                            ObjectMapper objectMapper,
                                            RedissonClient redissonClient,
                                            RiskDetailsService riskDetailsService,
                                            AcSnapshotPublishRecordService publishRecordService,
                                            AcSnapshotNodeRegistryService nodeRegistryService,
                                            AcSnapshotDirtyShardService dirtyShardService,
                                            RiskVocabularyAcMqProducer acMqProducer) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.redissonClient = redissonClient;
        this.riskDetailsService = riskDetailsService;
        this.publishRecordService = publishRecordService;
        this.nodeRegistryService = nodeRegistryService;
        this.dirtyShardService = dirtyShardService;
        this.acMqProducer = acMqProducer;
    }

    @Override
    public String publishAcSnapshot() {
        RLock lock = redissonClient.getLock(RiskVocabularyAcRedisKeys.PUBLISH_LOCK);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 5, TimeUnit.MINUTES);
            if (!locked) {
                return "\u5f53\u524d\u5df2\u6709 AC \u5feb\u7167\u53d1\u5e03\u4efb\u52a1\u6b63\u5728\u6267\u884c\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";
            }
            if (hasUnfinishedPublish()) {
                return "\u4e0a\u4e00\u6b21 AC \u5feb\u7167\u53d1\u5e03\u5c1a\u672a\u5b8c\u6210\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";
            }

            Long versionId = IdUtil.getSnowflake().nextId();
            LocalDateTime publishTime = LocalDateTime.now();
            RiskVocabularySnapshotManifest manifest = buildAndStoreSnapshot(versionId, publishTime);
            String latestHash = stringRedisTemplate.opsForValue().get(RiskVocabularyAcRedisKeys.LATEST_HASH);
            if (Objects.equals(manifest.getGlobalHash(), latestHash)) {
                deleteSnapshotKeys(manifest);
                markUndeletedKeywordsSynced();
                dirtyShardService.clearAll();
                return "AC \u5feb\u7167\u5185\u5bb9\u672a\u53d8\u5316\uff0c\u65e0\u9700\u91cd\u590d\u53d1\u5e03";
            }

            List<AcSnapshotNodeRegistryDO> targetNodes = nodeRegistryService.listActiveNodes(HEARTBEAT_TIMEOUT_SECONDS);
            AcSnapshotPublishRecordDO record = buildPublishRecord(manifest, targetNodes);
            RiskVocabularyAcPublishMessage message = RiskVocabularyAcPublishMessage.builder()
                    .messageType(RiskVocabularyAcPublishMessage.BUILD_VERSION)
                    .versionId(versionId)
                    .manifestKey(manifestKey(versionId))
                    .globalHash(manifest.getGlobalHash())
                    .totalWordCount(manifest.getTotalWordCount())
                    .publishTime(publishTime)
                    .build();

            acMqProducer.sendBuildMessageInTransaction(message, ignored -> publishRecordService.save(record));
            log.info("AC \u5feb\u7167\u53d1\u5e03\u4efb\u52a1\u5df2\u63d0\u4ea4\uff0cversionId={}\uff0cwordCount={}\uff0cshardCount={}\uff0ctargetNodeCount={}",
                    versionId, manifest.getTotalWordCount(), manifest.getShardCount(), record.getTargetNodeCount());
            return "AC \u5feb\u7167\u53d1\u5e03\u4efb\u52a1\u5df2\u63d0\u4ea4\uff0c\u7248\u672c\u53f7=" + versionId
                    + "\uff0c\u8bcd\u6761\u6570\u91cf=" + manifest.getTotalWordCount()
                    + "\uff0c\u7b49\u5f85\u8282\u70b9\u6784\u5efa\u786e\u8ba4";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("AC \u5feb\u7167\u53d1\u5e03\u88ab\u4e2d\u65ad", ex, BaseErrorCode.SERVICE_ERROR);
        } catch (Exception ex) {
            throw new ServiceException("AC \u5feb\u7167\u53d1\u5e03\u5931\u8d25\uff1a" + ex.getMessage(), ex, BaseErrorCode.SERVICE_ERROR);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private RiskVocabularySnapshotManifest buildAndStoreSnapshot(Long versionId, LocalDateTime publishTime) {
        List<RiskVocabularySnapshotShardMeta> shardMetas = new ArrayList<>();
        MessageDigest globalDigest = RiskVocabularyAcSupport.newSha256Digest();
        int totalWordCount = 0;

        for (RiskDetailsDO riskDetails : listActiveRiskDetails()) {
            List<RiskVocabularyKeywordDO> keywords = listKeywordsByRiskDetails(riskDetails.getId());
            if (CollectionUtils.isEmpty(keywords)) {
                continue;
            }
            List<RiskVocabularySnapshotShardMeta> riskDetailShardMetas =
                    storeRiskDetailShards(versionId, riskDetails.getId(), keywords);
            for (RiskVocabularySnapshotShardMeta shardMeta : riskDetailShardMetas) {
                shardMetas.add(shardMeta);
                totalWordCount += shardMeta.getWordCount();
                appendShardToGlobalDigest(globalDigest, shardMeta);
            }
        }

        String globalHash = RiskVocabularyAcSupport.toHex(globalDigest.digest());
        RiskVocabularySnapshotManifest manifest = RiskVocabularySnapshotManifest.builder()
                .versionId(versionId)
                .globalHash(globalHash)
                .totalWordCount(totalWordCount)
                .partitionType(PARTITION_TYPE_RISK_DETAILS)
                .shardCount(shardMetas.size())
                .publishTime(publishTime)
                .shards(shardMetas)
                .build();
        stringRedisTemplate.opsForValue().set(manifestKey(versionId), toJson(manifest));
        return manifest;
    }

    private List<RiskDetailsDO> listActiveRiskDetails() {
        return riskDetailsService.list(new LambdaQueryWrapper<RiskDetailsDO>()
                .eq(RiskDetailsDO::getDeleted, false)
                .eq(RiskDetailsDO::getStatus, 1)
                .orderByAsc(RiskDetailsDO::getId));
    }

    private boolean hasUnfinishedPublish() {
        return publishRecordService.count(new LambdaQueryWrapper<AcSnapshotPublishRecordDO>()
                .in(AcSnapshotPublishRecordDO::getStatus, UNFINISHED_PUBLISH_STATUSES)) > 0;
    }

    private List<RiskVocabularyKeywordDO> listKeywordsByRiskDetails(Long riskDetailsId) {
        List<RiskVocabularyKeywordDO> result = new ArrayList<>();
        long current = 1L;
        while (true) {
            Page<RiskVocabularyKeywordDO> page = page(new Page<>(current, PAGE_SIZE), new LambdaQueryWrapper<RiskVocabularyKeywordDO>()
                    .select(RiskVocabularyKeywordDO::getId,
                            RiskVocabularyKeywordDO::getRiskDetailsId,
                            RiskVocabularyKeywordDO::getKeyword,
                            RiskVocabularyKeywordDO::getRiskLevel)
                    .eq(RiskVocabularyKeywordDO::getDeleted, false)
                    .eq(RiskVocabularyKeywordDO::getRiskDetailsId, riskDetailsId)
                    .orderByAsc(RiskVocabularyKeywordDO::getKeyword)
                    .orderByAsc(RiskVocabularyKeywordDO::getRiskLevel)
                    .orderByAsc(RiskVocabularyKeywordDO::getId));
            result.addAll(page.getRecords());
            if (current >= page.getPages()) {
                break;
            }
            current++;
        }
        return result;
    }

    private List<RiskVocabularySnapshotShardMeta> storeRiskDetailShards(Long versionId,
                                                                        Long riskDetailsId,
                                                                        List<RiskVocabularyKeywordDO> keywords) {
        List<RiskVocabularySnapshotItem> items = RiskVocabularyAcSupport.toSnapshotItems(keywords);
        if (CollectionUtils.isEmpty(items)) {
            return List.of();
        }
        int bucketCount = Math.max(1, (items.size() + SHARD_BUCKET_SIZE - 1) / SHARD_BUCKET_SIZE);
        List<List<RiskVocabularySnapshotItem>> buckets = new ArrayList<>(bucketCount);
        for (int index = 0; index < bucketCount; index++) {
            buckets.add(new ArrayList<>());
        }
        for (RiskVocabularySnapshotItem item : items) {
            buckets.get(calculateBucketNo(item, bucketCount)).add(item);
        }

        List<RiskVocabularySnapshotShardMeta> shardMetas = new ArrayList<>();
        for (int bucketNo = 0; bucketNo < buckets.size(); bucketNo++) {
            List<RiskVocabularySnapshotItem> bucketItems = buckets.get(bucketNo);
            if (CollectionUtils.isEmpty(bucketItems)) {
                continue;
            }
            shardMetas.add(storeShard(versionId, riskDetailsId, bucketNo, bucketItems));
        }
        return shardMetas;
    }

    private RiskVocabularySnapshotShardMeta storeShard(Long versionId,
                                                       Long riskDetailsId,
                                                       int bucketNo,
                                                       List<RiskVocabularySnapshotItem> items) {
        items.sort(Comparator.comparing(RiskVocabularySnapshotItem::getKeyword,
                Comparator.nullsFirst(String::compareTo)));
        String shardHash = RiskVocabularyAcSupport.calculateHash(items);
        RiskVocabularySnapshotShard shard = RiskVocabularySnapshotShard.builder()
                .riskDetailsId(riskDetailsId)
                .bucketNo(bucketNo)
                .items(items)
                .build();
        String shardJson = toJson(shard);
        String shardKey = RiskVocabularyAcRedisKeys.shardKey(versionId, riskDetailsId, bucketNo);
        stringRedisTemplate.opsForValue().set(shardKey, shardJson);
        return RiskVocabularySnapshotShardMeta.builder()
                .riskDetailsId(riskDetailsId)
                .bucketNo(bucketNo)
                .shardKey(shardKey)
                .shardHash(shardHash)
                .wordCount(items.size())
                .byteSize(shardJson.getBytes(StandardCharsets.UTF_8).length)
                .build();
    }

    private int calculateBucketNo(RiskVocabularySnapshotItem item, int bucketCount) {
        if (bucketCount <= 1) {
            return 0;
        }
        String keyword = item.getKeyword() == null ? "" : item.getKeyword();
        return Math.floorMod(keyword.hashCode(), bucketCount);
    }

    private void appendShardToGlobalDigest(MessageDigest digest, RiskVocabularySnapshotShardMeta shardMeta) {
        RiskVocabularyAcSupport.updateDigest(digest, String.valueOf(shardMeta.getRiskDetailsId()));
        RiskVocabularyAcSupport.updateDigest(digest, "|");
        RiskVocabularyAcSupport.updateDigest(digest, String.valueOf(shardMeta.getBucketNo()));
        RiskVocabularyAcSupport.updateDigest(digest, "|");
        RiskVocabularyAcSupport.updateDigest(digest, shardMeta.getShardHash());
        RiskVocabularyAcSupport.updateDigest(digest, "|");
        RiskVocabularyAcSupport.updateDigest(digest, String.valueOf(shardMeta.getWordCount()));
        RiskVocabularyAcSupport.updateDigest(digest, "\n");
    }

    private AcSnapshotPublishRecordDO buildPublishRecord(RiskVocabularySnapshotManifest manifest,
                                                         List<AcSnapshotNodeRegistryDO> targetNodes) {
        List<String> targetNodeIds = targetNodes.stream()
                .map(AcSnapshotNodeRegistryDO::getNodeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        AcSnapshotPublishRecordDO record = new AcSnapshotPublishRecordDO();
        record.setVersionId(manifest.getVersionId());
        record.setManifestKey(manifestKey(manifest.getVersionId()));
        record.setGlobalHash(manifest.getGlobalHash());
        record.setWordCount(manifest.getTotalWordCount());
        record.setStatus(AcSnapshotPublishStatusEnums.SNAPSHOT_READY.getCode());
        record.setTargetNodeCount(targetNodeIds.size());
        record.setTargetNodeIds(String.join(",", targetNodeIds));
        record.setSuccessNodeCount(0);
        return record;
    }

    private void markUndeletedKeywordsSynced() {
        update(new LambdaUpdateWrapper<RiskVocabularyKeywordDO>()
                .eq(RiskVocabularyKeywordDO::getDeleted, false)
                .set(RiskVocabularyKeywordDO::getSyncStatus, SYNC_STATUS_SYNCED));
    }

    private void deleteSnapshotKeys(RiskVocabularySnapshotManifest manifest) {
        List<String> keys = new ArrayList<>();
        keys.add(manifestKey(manifest.getVersionId()));
        if (!CollectionUtils.isEmpty(manifest.getShards())) {
            manifest.getShards().forEach(shardMeta -> keys.add(shardMeta.getShardKey()));
        }
        stringRedisTemplate.delete(keys);
    }

    private String manifestKey(Long versionId) {
        return RiskVocabularyAcRedisKeys.manifestKey(versionId);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ServiceException("\u5e8f\u5217\u5316 AC \u5feb\u7167\u5931\u8d25", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
