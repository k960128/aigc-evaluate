package com.kant.llm.eval.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;
import com.kant.framework.exception.SecurityBlockException;
import com.kant.llm.eval.common.constant.RiskVocabularyAcRedisKeys;
import com.kant.llm.eval.common.convention.EvalContext;
import com.kant.llm.eval.common.enums.AcSnapshotAckStageEnums;
import com.kant.llm.eval.dao.entity.RiskVocabularyKeywordDO;
import com.kant.llm.eval.engine.model.AcAutomatonContext;
import com.kant.llm.eval.engine.model.RiskTag;
import com.kant.llm.eval.engine.model.RiskVocabularySnapshotItem;
import com.kant.llm.eval.engine.model.RiskVocabularySnapshotManifest;
import com.kant.llm.eval.engine.model.RiskVocabularySnapshotShard;
import com.kant.llm.eval.engine.model.RiskVocabularySnapshotShardMeta;
import com.kant.llm.eval.engine.support.RiskVocabularyAcSupport;
import com.kant.llm.eval.service.AcSnapshotNodeAckService;
import com.kant.llm.eval.service.AcSnapshotNodeRegistryService;
import com.kant.llm.eval.service.RiskVocabularyKeywordService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * L1 字面量风险拦截引擎。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class L1InterceptionEngine {

    private static final int RISK_LEVEL_BLOCK = 1;

    private static final int RISK_LEVEL_WARNING = 2;

    private static final boolean SYNC_STATUS_SYNCED = true;

    private final RiskVocabularyKeywordService riskVocabularyKeywordService;

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    private final AcSnapshotNodeAckService nodeAckService;

    private final AcSnapshotNodeRegistryService nodeRegistryService;

    private final AtomicReference<AcAutomatonContext> activeContext = new AtomicReference<>(emptyContext());

    private final AtomicReference<AcAutomatonContext> preparedContext = new AtomicReference<>();

    @Value("${spring.application.name:aigc-eval}")
    private String applicationName;

    @Value("${server.port:8800}")
    private Integer serverPort;

    @Getter
    private String nodeId;

    @Getter
    private String nodeHost;

    @PostConstruct
    public void init() {
        this.nodeHost = resolveHost();
        this.nodeId = applicationName + "-" + nodeHost + "-" + serverPort;
        if (!reloadLatestSnapshotFromRedis()) {
            reloadTrieFromSyncedDbKeywords();
        }
        heartbeat();
    }

    public EvalContext analyze(String prompt) {
        EvalContext context = new EvalContext(prompt);
        if (!StringUtils.hasText(prompt)) {
            return context;
        }

        AcAutomatonContext acContext = activeContext.get();
        AhoCorasickDoubleArrayTrie<RiskTag> trie = acContext == null ? null : acContext.getTrie();
        if (trie == null) {
            return context;
        }

        trie.parseText(prompt, (begin, end, riskTag) -> {
            if (riskTag == null || riskTag.getRiskLevel() == null) {
                return;
            }

            if (RISK_LEVEL_BLOCK == riskTag.getRiskLevel()) {
                String hitKeyword = substringSafely(prompt, begin, end);
                context.markL1Blocked(riskTag.getRiskDetailsId(), hitKeyword);
                throw new SecurityBlockException(riskTag.getRiskDetailsId(), hitKeyword);
            }

            if (RISK_LEVEL_WARNING == riskTag.getRiskLevel()) {
                context.addWarningTag(riskTag.getRiskDetailsId());
            }
        });

        return context;
    }

    public boolean prepareFromManifest(Long versionId, String manifestKey, String expectedHash) {
        try {
            AcAutomatonContext current = activeContext.get();
            if (current != null && current.getVersionId() != null && current.getVersionId() >= versionId) {
                nodeAckService.ackSuccess(versionId, nodeId, AcSnapshotAckStageEnums.BUILD.getCode(), current.getHash());
                return true;
            }
            AcAutomatonContext prepared = preparedContext.get();
            if (prepared != null && Objects.equals(prepared.getVersionId(), versionId)) {
                nodeAckService.ackSuccess(versionId, nodeId, AcSnapshotAckStageEnums.BUILD.getCode(), prepared.getHash());
                return true;
            }

            RiskVocabularySnapshotManifest manifest = loadManifest(manifestKey);
            if (manifest == null) {
                throw new IllegalStateException("AC manifest \u4e0d\u5b58\u5728\uff1a" + manifestKey);
            }
            if (!Objects.equals(versionId, manifest.getVersionId())) {
                throw new IllegalStateException("AC manifest \u7248\u672c\u4e0d\u4e00\u81f4\uff1a" + manifestKey);
            }
            if (!Objects.equals(expectedHash, manifest.getGlobalHash())) {
                throw new IllegalStateException("AC manifest hash \u4e0d\u4e00\u81f4\uff1a" + manifestKey);
            }

            AcAutomatonContext newContext = buildContext(manifest);
            preparedContext.set(newContext);
            nodeAckService.ackSuccess(versionId, nodeId, AcSnapshotAckStageEnums.BUILD.getCode(), newContext.getHash());
            heartbeat();
            log.info("AC \u5feb\u7167\u5df2\u6784\u5efa\u5230 preparedContext\uff0cversionId={}\uff0cwordCount={}\uff0chash={}",
                    versionId, newContext.getWordCount(), newContext.getHash());
            return true;
        } catch (Exception ex) {
            nodeAckService.ackFailed(versionId, nodeId, AcSnapshotAckStageEnums.BUILD.getCode(), expectedHash, ex.getMessage());
            log.warn("AC \u5feb\u7167\u6784\u5efa\u5931\u8d25\uff0cversionId={}", versionId, ex);
            return false;
        }
    }

    public boolean activatePreparedVersion(Long versionId, String expectedHash) {
        try {
            AcAutomatonContext current = activeContext.get();
            if (current != null && Objects.equals(current.getVersionId(), versionId)) {
                nodeAckService.ackSuccess(versionId, nodeId, AcSnapshotAckStageEnums.ACTIVE.getCode(), current.getHash());
                return true;
            }
            String latestVersion = stringRedisTemplate.opsForValue().get(RiskVocabularyAcRedisKeys.LATEST_VERSION);
            if (!Objects.equals(String.valueOf(versionId), latestVersion)) {
                throw new IllegalStateException("Redis \u6700\u65b0 AC \u7248\u672c\u5c1a\u672a\u5207\u6362\u5230 " + versionId);
            }
            AcAutomatonContext prepared = preparedContext.get();
            if (prepared == null || !Objects.equals(prepared.getVersionId(), versionId)) {
                throw new IllegalStateException("\u8282\u70b9\u672a\u51c6\u5907\u597d AC \u5feb\u7167\uff0cversionId=" + versionId);
            }
            if (!Objects.equals(prepared.getHash(), expectedHash)) {
                throw new IllegalStateException("\u8282\u70b9\u5df2\u51c6\u5907 AC \u5feb\u7167 hash \u4e0d\u4e00\u81f4\uff0cversionId=" + versionId);
            }
            activeContext.set(prepared);
            preparedContext.compareAndSet(prepared, null);
            nodeAckService.ackSuccess(versionId, nodeId, AcSnapshotAckStageEnums.ACTIVE.getCode(), prepared.getHash());
            heartbeat();
            log.info("AC \u5feb\u7167\u5df2\u6fc0\u6d3b\uff0cversionId={}\uff0cwordCount={}\uff0chash={}",
                    versionId, prepared.getWordCount(), prepared.getHash());
            return true;
        } catch (Exception ex) {
            nodeAckService.ackFailed(versionId, nodeId, AcSnapshotAckStageEnums.ACTIVE.getCode(), expectedHash, ex.getMessage());
            log.warn("AC \u5feb\u7167\u6fc0\u6d3b\u5931\u8d25\uff0cversionId={}", versionId, ex);
            return false;
        }
    }

    public AcAutomatonContext getActiveContext() {
        return activeContext.get();
    }

    public AcAutomatonContext getPreparedContext() {
        return preparedContext.get();
    }

    public void heartbeat() {
        AcAutomatonContext current = activeContext.get();
        AcAutomatonContext prepared = preparedContext.get();
        nodeRegistryService.heartbeat(nodeId, applicationName, nodeHost,
                current == null ? null : current.getVersionId(),
                prepared == null ? null : prepared.getVersionId());
    }

    private boolean reloadLatestSnapshotFromRedis() {
        try {
            String manifestKey = stringRedisTemplate.opsForValue().get(RiskVocabularyAcRedisKeys.LATEST_MANIFEST);
            if (!StringUtils.hasText(manifestKey)) {
                String latestVersion = stringRedisTemplate.opsForValue().get(RiskVocabularyAcRedisKeys.LATEST_VERSION);
                if (!StringUtils.hasText(latestVersion)) {
                    return false;
                }
                manifestKey = RiskVocabularyAcRedisKeys.manifestKey(Long.valueOf(latestVersion));
            }
            RiskVocabularySnapshotManifest manifest = loadManifest(manifestKey);
            if (manifest == null) {
                return false;
            }
            AcAutomatonContext context = buildContext(manifest);
            activeContext.set(context);
            nodeAckService.ackSuccess(manifest.getVersionId(), nodeId,
                    AcSnapshotAckStageEnums.ACTIVE.getCode(), context.getHash());
            log.info("\u5df2\u4ece Redis manifest \u52a0\u8f7d AC \u5feb\u7167\uff0cversionId={}\uff0cwordCount={}\uff0chash={}",
                    manifest.getVersionId(), manifest.getTotalWordCount(), manifest.getGlobalHash());
            return true;
        } catch (Exception ex) {
            log.warn("\u4ece Redis manifest \u52a0\u8f7d AC \u5feb\u7167\u5931\u8d25\uff0c\u964d\u7ea7\u4f7f\u7528 DB \u5df2\u540c\u6b65\u8bcd\u6761", ex);
            return false;
        }
    }

    private void reloadTrieFromSyncedDbKeywords() {
        List<RiskVocabularyKeywordDO> keywords = riskVocabularyKeywordService.lambdaQuery()
                .eq(RiskVocabularyKeywordDO::getSyncStatus, SYNC_STATUS_SYNCED)
                .eq(RiskVocabularyKeywordDO::getDeleted, false)
                .list();

        List<RiskVocabularySnapshotItem> items = RiskVocabularyAcSupport.toSnapshotItems(keywords);
        String hash = RiskVocabularyAcSupport.calculateHash(items);
        activeContext.set(buildContext(null, hash, items));
        log.info("\u5df2\u4ece DB \u5df2\u540c\u6b65\u8bcd\u6761\u52a0\u8f7d AC \u5feb\u7167\uff0cwordCount={}\uff0chash={}", items.size(), hash);
    }

    private RiskVocabularySnapshotManifest loadManifest(String manifestKey) {
        String manifestJson = stringRedisTemplate.opsForValue().get(manifestKey);
        if (!StringUtils.hasText(manifestJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(manifestJson, RiskVocabularySnapshotManifest.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("\u53cd\u5e8f\u5217\u5316 AC manifest \u5931\u8d25\uff1a" + manifestKey, ex);
        }
    }

    private RiskVocabularySnapshotShard loadShard(String shardKey) {
        String shardJson = stringRedisTemplate.opsForValue().get(shardKey);
        if (!StringUtils.hasText(shardJson)) {
            throw new IllegalStateException("AC shard \u4e0d\u5b58\u5728\uff1a" + shardKey);
        }
        try {
            return objectMapper.readValue(shardJson, RiskVocabularySnapshotShard.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("\u53cd\u5e8f\u5217\u5316 AC shard \u5931\u8d25\uff1a" + shardKey, ex);
        }
    }

    private AcAutomatonContext buildContext(RiskVocabularySnapshotManifest manifest) {
        List<RiskVocabularySnapshotItem> items = new ArrayList<>();
        MessageDigest globalDigest = RiskVocabularyAcSupport.newSha256Digest();
        int wordCount = 0;
        if (!CollectionUtils.isEmpty(manifest.getShards())) {
            for (RiskVocabularySnapshotShardMeta shardMeta : manifest.getShards()) {
                RiskVocabularySnapshotShard shard = loadShard(shardMeta.getShardKey());
                if (!Objects.equals(shardMeta.getRiskDetailsId(), shard.getRiskDetailsId())
                        || !Objects.equals(shardMeta.getBucketNo(), shard.getBucketNo())) {
                    throw new IllegalStateException("AC shard \u8eab\u4efd\u4fe1\u606f\u4e0d\u4e00\u81f4\uff1a" + shardMeta.getShardKey());
                }
                List<RiskVocabularySnapshotItem> shardItems = shard.getItems() == null ? List.of() : shard.getItems();
                if (!Objects.equals(shardMeta.getWordCount(), shardItems.size())) {
                    throw new IllegalStateException("AC shard \u8bcd\u6761\u6570\u91cf\u4e0d\u4e00\u81f4\uff1a" + shardMeta.getShardKey());
                }
                String actualShardHash = RiskVocabularyAcSupport.calculateHash(shardItems);
                if (!Objects.equals(shardMeta.getShardHash(), actualShardHash)) {
                    throw new IllegalStateException("AC shard hash \u4e0d\u4e00\u81f4\uff1a" + shardMeta.getShardKey());
                }
                wordCount += shardItems.size();
                items.addAll(shardItems);
                appendShardToGlobalDigest(globalDigest, shardMeta);
            }
        }
        String actualGlobalHash = RiskVocabularyAcSupport.toHex(globalDigest.digest());
        if (!Objects.equals(manifest.getGlobalHash(), actualGlobalHash)) {
            throw new IllegalStateException("AC manifest \u5168\u5c40 hash \u4e0d\u4e00\u81f4\uff0cversionId=" + manifest.getVersionId());
        }
        if (!Objects.equals(manifest.getTotalWordCount(), wordCount)) {
            throw new IllegalStateException("AC manifest \u8bcd\u6761\u6570\u91cf\u4e0d\u4e00\u81f4\uff0cversionId=" + manifest.getVersionId());
        }
        return AcAutomatonContext.builder()
                .versionId(manifest.getVersionId())
                .hash(manifest.getGlobalHash())
                .wordCount(wordCount)
                .buildTime(LocalDateTime.now())
                .trie(RiskVocabularyAcSupport.buildTrie(items))
                .build();
    }

    private AcAutomatonContext buildContext(Long versionId, String hash, List<RiskVocabularySnapshotItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            items = List.of();
        }
        String actualHash = RiskVocabularyAcSupport.calculateHash(items);
        if (!Objects.equals(hash, actualHash)) {
            throw new IllegalStateException("AC \u5feb\u7167 hash \u6821\u9a8c\u5931\u8d25\uff0cversionId=" + versionId);
        }
        return AcAutomatonContext.builder()
                .versionId(versionId)
                .hash(hash)
                .wordCount(items.size())
                .buildTime(LocalDateTime.now())
                .trie(RiskVocabularyAcSupport.buildTrie(items))
                .build();
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

    private static AcAutomatonContext emptyContext() {
        return AcAutomatonContext.builder()
                .wordCount(0)
                .buildTime(LocalDateTime.now())
                .trie(RiskVocabularyAcSupport.buildTrie(List.of()))
                .build();
    }

    private static String substringSafely(String source, int begin, int end) {
        if (source == null || begin >= end) {
            return "";
        }
        int safeBegin = Math.max(0, begin);
        int safeEnd = Math.min(source.length(), end);
        if (safeBegin >= safeEnd) {
            return "";
        }
        return source.substring(safeBegin, safeEnd);
    }

    private static String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            return "unknown-host";
        }
    }
}
