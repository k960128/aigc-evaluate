package com.kant.llm.eval.fingerprint.engine;

import cn.hutool.core.collection.CollectionUtil;
import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;
import com.kant.llm.common.convention.L1EvalContext;
import com.kant.llm.eval.fingerprint.dao.entity.KnowledgeNegativeFingerprintDO;
import com.kant.llm.eval.fingerprint.service.KnowledgeNegativeFingerprintService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 构建负向指纹的AC自动机Trie树
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NegativeFingerprintACTrieManager {

    private final KnowledgeNegativeFingerprintService knowledgeNegativeFingerprintService;

    /**
     * 存储每个 risk_details_id 对应的AC自动机
     * Key: risk_details_id
     * Value: AhoCorasickDoubleArrayTrie
     */
    private final Map<Long, AhoCorasickDoubleArrayTrie<String>> trieMap = new ConcurrentHashMap<>();

    /**
     * 存储每个 risk_details_id 对应的关键词列表（用于调试或后续扩展）
     */
    private final Map<Long, List<String>> keywordMap = new ConcurrentHashMap<>();

    /**
     * 系统启动时自动构建AC自动机
     */
    @PostConstruct
    public void init() {
        buildTries();
    }

    /**
     * 从数据库读取数据并按 risk_details_id 构建AC自动机
     */
    public void buildTries() {
        log.info("开始构建AC自动机...");
        long startTime = System.currentTimeMillis();

        try {
            // 1. 查询所有有效关键词
            List<KnowledgeNegativeFingerprintDO> keywordList = knowledgeNegativeFingerprintService.list();

            if (keywordList == null || keywordList.isEmpty()) {
                log.warn("未查询到任何关键词数据");
                return;
            }

            log.info("共查询到 {} 条关键词记录", keywordList.size());

            // 2. 按 risk_details_id 分组
            Map<Long, TreeMap<String, String>> groupMap = new HashMap<>();

            for (KnowledgeNegativeFingerprintDO keyword : keywordList) {
                Long riskDetailsId = keyword.getRiskDetailsId();
                String keywordStr = keyword.getFingerprintText();

                // 过滤空关键词
                if (keywordStr == null || keywordStr.trim().isEmpty()) {
                    continue;
                }

                // 获取或创建该分组的数据容器
                TreeMap<String, String> map = groupMap.computeIfAbsent(riskDetailsId, k -> new TreeMap<>());

                // 使用 keyword 本身作为 key 和 value
                // 如果有特殊需求，可以将 value 设置为其他信息（如ID）
                map.put(keywordStr, keywordStr);
            }

            // 3. 为每个分组构建AC自动机
            int buildCount = 0;
            for (Map.Entry<Long, TreeMap<String, String>> entry : groupMap.entrySet()) {
                Long riskDetailsId = entry.getKey();
                TreeMap<String, String> map = entry.getValue();

                if (map.isEmpty()) {
                    continue;
                }

                // 构建AC自动机
                AhoCorasickDoubleArrayTrie<String> trie = new AhoCorasickDoubleArrayTrie<>();
                trie.build(map);

                // 存储到Map中
                trieMap.put(riskDetailsId, trie);
                keywordMap.put(riskDetailsId, new ArrayList<>(map.keySet()));

                buildCount++;
                log.info("已构建 risk_details_id = {} 的AC自动机，包含 {} 个关键词", riskDetailsId, map.size());
            }

            long endTime = System.currentTimeMillis();
            log.info("AC自动机构建完成！共构建 {} 个自动机，耗时 {} ms", buildCount, (endTime - startTime));

        } catch (Exception e) {
            log.error("构建AC自动机失败", e);
            throw new RuntimeException("AC自动机构建失败", e);
        }
    }

    /**
     * 重新构建所有AC自动机（用于数据更新后刷新）
     */
    public void rebuild() {
        log.info("开始重新构建AC自动机...");
        // 清空旧数据
        trieMap.clear();
        keywordMap.clear();
        // 重新构建
        buildTries();
    }

    /**
     * 根据 risk_details_id 获取对应的AC自动机
     */
    public AhoCorasickDoubleArrayTrie<String> getTrieByRiskDetailsId(Long riskDetailsId) {
        return trieMap.get(riskDetailsId);
    }

    /**
     * 根据 risk_details_id 匹配文本
     * @param riskDetailsId 风险小类ID
     * @param text 待匹配文本
     * @return 匹配结果列表
     */
    public L1EvalContext match(Long riskDetailsId, String text) {
        AhoCorasickDoubleArrayTrie<String> trie = trieMap.get(riskDetailsId);
        if (trie == null) {
            log.warn("未找到 risk_details_id = {} 的AC自动机", riskDetailsId);
            return L1EvalContext
                    .builder()
                    .l1Blocked(false)
                    .riskDetailsId(riskDetailsId)
                    .originalPrompt(text)
                    .build();
        }
        List<AhoCorasickDoubleArrayTrie.Hit<String>> hits = trie.parseText(text);
        if (CollectionUtil.isEmpty(hits)) {
            return L1EvalContext
                    .builder()
                    .l1Blocked(false)
                    .riskDetailsId(riskDetailsId)
                    .originalPrompt(text)
                    .build();
        }
        return L1EvalContext
                .builder()
                .l1Blocked(true)
                .blockKeyWords(hits.stream().map(hit ->
                                new L1EvalContext.BlockKeyWordEntity(
                                        hit.value,
                                        hit.begin,
                                        hit.end))
                        .collect(Collectors.toList()))
                .riskDetailsId(riskDetailsId)
                .originalPrompt(text)
                .build();
    }

    /**
     * 根据 risk_details_id 匹配文本（使用回调方式，更高效）
     */
    public void matchWithCallback(Long riskDetailsId, String text, AhoCorasickDoubleArrayTrie.IHit<String> handler) {
        AhoCorasickDoubleArrayTrie<String> trie = trieMap.get(riskDetailsId);
        if (trie == null) {
            log.warn("未找到 risk_details_id = {} 的AC自动机", riskDetailsId);
            return;
        }
        trie.parseText(text, handler);
    }

    /**
     * 匹配所有 risk_details_id 的AC自动机
     * @param text 待匹配文本
     * @return Map<risk_details_id, List<匹配结果>>
     */
    public Map<Long, List<AhoCorasickDoubleArrayTrie.Hit<String>>> matchAll(String text) {
        Map<Long, List<AhoCorasickDoubleArrayTrie.Hit<String>>> resultMap = new HashMap<>();

        for (Map.Entry<Long, AhoCorasickDoubleArrayTrie<String>> entry : trieMap.entrySet()) {
            Long riskDetailsId = entry.getKey();
            AhoCorasickDoubleArrayTrie<String> trie = entry.getValue();
            List<AhoCorasickDoubleArrayTrie.Hit<String>> hits = trie.parseText(text);
            if (!hits.isEmpty()) {
                resultMap.put(riskDetailsId, hits);
            }
        }

        return resultMap;
    }

    /**
     * 获取所有 risk_details_id
     */
    public Set<Long> getAllRiskDetailsIds() {
        return trieMap.keySet();
    }

    /**
     * 获取某个 risk_details_id 的关键词数量
     */
    public int getKeywordCount(Long riskDetailsId) {
        List<String> keywords = keywordMap.get(riskDetailsId);
        return keywords == null ? 0 : keywords.size();
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRiskDetailsIds", trieMap.size());
        stats.put("totalKeywords", keywordMap.values().stream().mapToInt(List::size).sum());

        Map<Long, Integer> detailCounts = new HashMap<>();
        for (Map.Entry<Long, List<String>> entry : keywordMap.entrySet()) {
            detailCounts.put(entry.getKey(), entry.getValue().size());
        }
        stats.put("detailKeywordCounts", detailCounts);

        return stats;
    }
}
