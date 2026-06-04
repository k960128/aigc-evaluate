package com.kant.llm.eval.engine;

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;
import com.kant.llm.eval.common.convention.EvalContext;
import com.kant.llm.eval.common.exception.SecurityBlockException;
import com.kant.llm.eval.dao.entity.RiskVocabularyKeywordDO;
import com.kant.llm.eval.engine.model.RiskTag;
import com.kant.llm.eval.service.RiskVocabularyKeywordService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.TreeMap;

/**
 * 基于 Aho-Corasick Double Array Trie 的 L1 字面量极速拦截引擎。
 *
 * <p>引擎内部只维护一个全局可见的 Trie 引用：{@link #activeTrie}。
 * 在线评测线程只读取一次 volatile 引用，并基于该不可变快照完成扫描。
 * 词库重载时会先在局部变量中构建一棵全新的 Trie，构建成功后再通过一次 volatile
 * 赋值发布。该双缓冲策略可以保证热路径 {@link #analyze(String)} 无锁执行，
 * 避免词库重载阻塞网关流量。</p>
 *
 * <p>每个 Trie 叶子节点挂载一个 {@link RiskTag} 载荷。载荷中包含特征词 ID、
 * 风险明细 ID 和风险等级，因此命中后可以立即完成分级路由，无需二次查询。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class L1InterceptionEngine {

    private static final int RISK_LEVEL_BLOCK = 1;

    private static final int RISK_LEVEL_WARNING = 2;

    private static final int SYNC_STATUS_SYNCED = 1;

    private final RiskVocabularyKeywordService riskVocabularyKeywordService;

    /**
     * 评测线程使用的唯一全局 AC 自动机 Trie。
     *
     * <p>该对象一旦发布就不再原地修改。reload 必须先构建局部新 Trie，
     * 等构建成功后再原子替换当前引用。</p>
     */
    private volatile AhoCorasickDoubleArrayTrie<RiskTag> activeTrie = buildTrie(new TreeMap<>());

    /**
     * Spring 容器启动时初始化 L1 词库。
     */
    @PostConstruct
    public void init() {
        reloadTrie();
    }

    /**
     * 将全量有效且已同步的风险词库重载到一棵新的 Trie 中。
     *
     * <p>该方法不会加锁阻塞评测线程。新 Trie 构建期间，已有请求继续使用旧 Trie
     * 快照；新 Trie 准备完成后，通过一次 volatile 写入发布给后续请求。</p>
     */
    public void reloadTrie() {
        List<RiskVocabularyKeywordDO> keywords = riskVocabularyKeywordService.lambdaQuery()
                .eq(RiskVocabularyKeywordDO::getSyncStatus, SYNC_STATUS_SYNCED)
                .eq(RiskVocabularyKeywordDO::getDeleted, false)
                .list();

        TreeMap<String, RiskTag> dictionary = new TreeMap<>();
        if (!CollectionUtils.isEmpty(keywords)) {
            for (RiskVocabularyKeywordDO keyword : keywords) {
                appendKeyword(dictionary, keyword);
            }
        }

        this.activeTrie = buildTrie(dictionary);
        log.info("AC自动机同步完成, keywordSize={}", dictionary.size());
    }

    /**
     * 通过一次 O(N) Aho-Corasick 扫描分析 Prompt。
     *
     * <p>风险等级 1 表示致命命中，会立即抛出 {@link SecurityBlockException}
     * 并中断扫描。风险等级 2 表示疑似命中，其风险明细 ID 会被收集到
     * {@link EvalContext#getHitWarningTags()} 中，并继续扫描以供下游 L2 评测使用。</p>
     *
     * @param prompt 原始模型请求 Prompt
     * @return 写入 L1 疑似风险标签后的评测上下文
     */
    public EvalContext analyze(String prompt) {
        EvalContext context = new EvalContext(prompt);
        if (!StringUtils.hasText(prompt)) {
            return context;
        }

        AhoCorasickDoubleArrayTrie<RiskTag> trie = this.activeTrie;
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

    private void appendKeyword(TreeMap<String, RiskTag> dictionary, RiskVocabularyKeywordDO keyword) {
        if (keyword == null
                || !StringUtils.hasText(keyword.getKeyword())
                || keyword.getRiskLevel() == null
                || keyword.getRiskDetailsId() == null) {
            return;
        }

        String literal = keyword.getKeyword().trim();
        RiskTag riskTag = new RiskTag(keyword.getId(), keyword.getRiskDetailsId(), keyword.getRiskLevel());
        dictionary.merge(literal, riskTag, L1InterceptionEngine::selectHigherPriorityTag);
    }

    private static RiskTag selectHigherPriorityTag(RiskTag existing, RiskTag incoming) {
        if (existing == null) {
            return incoming;
        }
        if (incoming == null || incoming.getRiskLevel() == null) {
            return existing;
        }
        if (existing.getRiskLevel() == null) {
            return incoming;
        }
        return incoming.getRiskLevel() < existing.getRiskLevel() ? incoming : existing;
    }

    private static AhoCorasickDoubleArrayTrie<RiskTag> buildTrie(TreeMap<String, RiskTag> dictionary) {
        AhoCorasickDoubleArrayTrie<RiskTag> trie = new AhoCorasickDoubleArrayTrie<>();
        trie.build(dictionary == null ? new TreeMap<>() : dictionary);
        return trie;
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
}
