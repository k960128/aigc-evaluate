package com.kant.llm.eval.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;
import com.kant.llm.eval.common.constant.RiskVocabularyAcRedisKeys;
import com.kant.llm.eval.common.convention.EvalContext;
import com.kant.llm.eval.common.exception.SecurityBlockException;
import com.kant.llm.eval.dao.entity.RiskVocabularyKeywordDO;
import com.kant.llm.eval.engine.model.AcAutomatonContext;
import com.kant.llm.eval.engine.model.RiskTag;
import com.kant.llm.eval.engine.model.RiskVocabularyPublishMessage;
import com.kant.llm.eval.engine.model.RiskVocabularySnapshot;
import com.kant.llm.eval.engine.model.RiskVocabularySnapshotItem;
import com.kant.llm.eval.engine.support.RiskVocabularyAcSupport;
import com.kant.llm.eval.service.RiskVocabularyKeywordService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * L1 字面量风险拦截引擎。
 *
 * <p>引擎只维护当前生效的 AC 自动机上下文。新版本在后台完整构建并校验成功后，
 * 再通过 AtomicReference 原子替换，保证评测热路径无锁且不会读到半成品 Trie。</p>
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

    /**
     * 当前 JVM 正在使用的 AC 自动机版本。
     */
    private final AtomicReference<AcAutomatonContext> activeContext = new AtomicReference<>(emptyContext());

    /**
     * 应用启动时优先加载 Redis latest 快照；Redis 不可用或快照缺失时，回退到 DB 已同步词条构建。
     */
    @PostConstruct
    public void init() {
        if (reloadLatestSnapshotFromRedis()) {
            return;
        }
        reloadTrieFromSyncedDbKeywords();
    }

    /**
     * 使用当前生效的 AC 自动机扫描 prompt。
     *
     * <p>方法内只读取一次 activeContext，后续扫描都基于该不可变快照，避免热更新过程中前后不一致。</p>
     */
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

    /**
     * 根据 Redis Pub/Sub 发布消息加载并切换到新版本 AC。
     *
     * <p>该方法由监听器的单线程构建执行器调用。构建失败会抛出异常给监听器重试，
     * 当前 activeContext 不会被修改。</p>
     */
    public boolean rebuildFromPublishMessage(RiskVocabularyPublishMessage message) {
        if (message == null || message.getVersionId() == null || !StringUtils.hasText(message.getSnapshotKey())) {
            log.warn("跳过无效的 AC 自动机发布消息：{}", message);
            return false;
        }

        AcAutomatonContext current = activeContext.get();
        if (current != null && current.getVersionId() != null
                && current.getVersionId() >= message.getVersionId()) {
            // Snowflake 版本号递增，旧消息或重复消息不能覆盖当前较新的 AC。
            log.info("跳过过期的 AC 自动机版本，当前版本={}，消息版本={}",
                    current.getVersionId(), message.getVersionId());
            return true;
        }

        RiskVocabularySnapshot snapshot = loadSnapshot(message.getSnapshotKey());
        if (snapshot == null) {
            throw new IllegalStateException("未找到 AC 自动机快照：" + message.getSnapshotKey());
        }
        if (!Objects.equals(message.getVersionId(), snapshot.getVersionId())) {
            throw new IllegalStateException("AC 自动机快照版本不一致，消息版本="
                    + message.getVersionId() + "，快照版本=" + snapshot.getVersionId());
        }
        if (!Objects.equals(message.getHash(), snapshot.getHash())) {
            throw new IllegalStateException("AC 自动机快照哈希不一致，版本号=" + message.getVersionId());
        }
        if (!Objects.equals(message.getWordCount(), snapshot.getWordCount())) {
            throw new IllegalStateException("AC 自动机快照词条数量不一致，版本号=" + message.getVersionId());
        }

        AcAutomatonContext newContext = buildContext(snapshot);
        // 只有完整构建和校验成功后才替换引用；失败时继续使用旧 AC。
        activeContext.set(newContext);
        log.info("AC 自动机版本切换完成，版本号={}，特征词数量={}，快照哈希={}",
                newContext.getVersionId(), newContext.getWordCount(), newContext.getHash());
        return true;
    }

    /**
     * 暴露当前版本上下文，便于后续排查节点加载状态或扩展管理接口。
     */
    public AcAutomatonContext getActiveContext() {
        return activeContext.get();
    }

    /**
     * 从 Redis latest 指针加载最新快照。
     */
    private boolean reloadLatestSnapshotFromRedis() {
        try {
            String latestVersion = stringRedisTemplate.opsForValue().get(RiskVocabularyAcRedisKeys.LATEST_VERSION);
            if (!StringUtils.hasText(latestVersion)) {
                return false;
            }
            Long versionId = Long.valueOf(latestVersion);
            String snapshotKey = RiskVocabularyAcRedisKeys.snapshotKey(versionId);
            RiskVocabularySnapshot snapshot = loadSnapshot(snapshotKey);
            if (snapshot == null) {
                log.warn("Redis 中缺少最新 AC 自动机快照，版本号={}", versionId);
                return false;
            }
            activeContext.set(buildContext(snapshot));
            log.info("从 Redis 初始化 AC 自动机完成，版本号={}，特征词数量={}，快照哈希={}",
                    snapshot.getVersionId(), snapshot.getWordCount(), snapshot.getHash());
            return true;
        } catch (Exception ex) {
            log.warn("从 Redis 初始化 AC 自动机失败，回退到 DB 已同步词条构建", ex);
            return false;
        }
    }

    /**
     * Redis 快照不可用时的启动兜底策略。
     *
     * <p>这里只读取 syncStatus=true 的 DB 词条，保持和历史实现兼容。</p>
     */
    private void reloadTrieFromSyncedDbKeywords() {
        List<RiskVocabularyKeywordDO> keywords = riskVocabularyKeywordService.lambdaQuery()
                .eq(RiskVocabularyKeywordDO::getSyncStatus, SYNC_STATUS_SYNCED)
                .eq(RiskVocabularyKeywordDO::getDeleted, false)
                .list();

        List<RiskVocabularySnapshotItem> items = RiskVocabularyAcSupport.toSnapshotItems(keywords);
        String hash = RiskVocabularyAcSupport.calculateHash(items);
        RiskVocabularySnapshot snapshot = RiskVocabularySnapshot.builder()
                .versionId(null)
                .hash(hash)
                .wordCount(items.size())
                .publishTime(LocalDateTime.now())
                .items(items)
                .build();
        activeContext.set(buildContext(snapshot));
        log.info("从 DB 兜底初始化 AC 自动机完成，特征词数量={}，快照哈希={}", items.size(), hash);
    }

    /**
     * 根据 Redis snapshotKey 读取并反序列化快照。
     */
    private RiskVocabularySnapshot loadSnapshot(String snapshotKey) {
        String snapshotJson = stringRedisTemplate.opsForValue().get(snapshotKey);
        if (!StringUtils.hasText(snapshotJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(snapshotJson, RiskVocabularySnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("反序列化 AC 自动机快照失败：" + snapshotKey, ex);
        }
    }

    /**
     * 校验快照内容并构建新的 AC 上下文。
     */
    private AcAutomatonContext buildContext(RiskVocabularySnapshot snapshot) {
        List<RiskVocabularySnapshotItem> items = snapshot.getItems();
        if (CollectionUtils.isEmpty(items)) {
            items = List.of();
        }
        String actualHash = RiskVocabularyAcSupport.calculateHash(items);
        if (!Objects.equals(snapshot.getHash(), actualHash)) {
            throw new IllegalStateException("AC 自动机快照内容哈希校验失败，版本号=" + snapshot.getVersionId());
        }

        return AcAutomatonContext.builder()
                .versionId(snapshot.getVersionId())
                .hash(snapshot.getHash())
                .wordCount(items.size())
                .buildTime(LocalDateTime.now())
                .trie(RiskVocabularyAcSupport.buildTrie(items))
                .build();
    }

    /**
     * 空 AC 上下文，保证服务启动早期即使还没有词库也可以安全调用 analyze。
     */
    private static AcAutomatonContext emptyContext() {
        return AcAutomatonContext.builder()
                .wordCount(0)
                .buildTime(LocalDateTime.now())
                .trie(RiskVocabularyAcSupport.buildTrie(List.of()))
                .build();
    }

    /**
     * AC 回调返回的是 begin/end 下标，这里做边界保护后再截取命中的原始文本。
     */
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
}
