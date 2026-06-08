package com.kant.llm.eval.engine.support;

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;
import com.kant.llm.eval.dao.entity.RiskVocabularyKeywordDO;
import com.kant.llm.eval.engine.model.RiskTag;
import com.kant.llm.eval.engine.model.RiskVocabularySnapshotItem;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * 风险词库 AC 自动机构建的公共辅助类。
 *
 * <p>发布服务生成 Redis 快照、节点加载快照构建 Trie 都复用这里的规则，确保“发布时算出的
 * hash”和“节点实际构建的内容”口径一致。</p>
 */
public final class RiskVocabularyAcSupport {

    private RiskVocabularyAcSupport() {
    }

    /**
     * 将数据库词条转换成快照词条。
     *
     * <p>这里会过滤无效词条、去除关键词首尾空格，并对重复关键词只保留最高优先级的风险标签。</p>
     */
    public static List<RiskVocabularySnapshotItem> toSnapshotItems(List<RiskVocabularyKeywordDO> keywords) {
        TreeMap<String, RiskVocabularySnapshotItem> dictionary = new TreeMap<>();
        if (!CollectionUtils.isEmpty(keywords)) {
            for (RiskVocabularyKeywordDO keyword : keywords) {
                appendKeyword(dictionary, keyword);
            }
        }
        return new ArrayList<>(dictionary.values());
    }

    /**
     * 对快照内容计算稳定 SHA-256 hash。
     *
     * <p>hash 内容只包含会影响 AC 匹配和命中结果的字段；排序后再计算，避免 DB 返回顺序导致
     * 同一批词条产生不同版本。</p>
     */
    public static String calculateHash(List<RiskVocabularySnapshotItem> items) {
        StringBuilder content = new StringBuilder();
        if (!CollectionUtils.isEmpty(items)) {
            items.stream()
                    .sorted(Comparator.comparing(RiskVocabularySnapshotItem::getKeyword,
                            Comparator.nullsFirst(String::compareTo)))
                    .forEach(item -> content.append(nullToEmpty(item.getKeyword())).append('|')
                            .append(nullToEmpty(item.getKeywordId())).append('|')
                            .append(nullToEmpty(item.getRiskDetailsId())).append('|')
                            .append(nullToEmpty(item.getRiskLevel()))
                            .append('\n'));
        }
        return sha256(content.toString());
    }

    /**
     * 基于快照词条构建 Aho-Corasick Double Array Trie。
     *
     * <p>构建完成后的 Trie 不再原地修改，只通过 L1 引擎的 AtomicReference 整体切换。</p>
     */
    public static AhoCorasickDoubleArrayTrie<RiskTag> buildTrie(List<RiskVocabularySnapshotItem> items) {
        TreeMap<String, RiskTag> dictionary = new TreeMap<>();
        if (!CollectionUtils.isEmpty(items)) {
            for (RiskVocabularySnapshotItem item : items) {
                appendSnapshotItem(dictionary, item);
            }
        }
        AhoCorasickDoubleArrayTrie<RiskTag> trie = new AhoCorasickDoubleArrayTrie<>();
        trie.build(dictionary);
        return trie;
    }

    /**
     * DB 词条进入快照前的过滤和归一化逻辑。
     */
    private static void appendKeyword(TreeMap<String, RiskVocabularySnapshotItem> dictionary,
                                      RiskVocabularyKeywordDO keyword) {
        if (keyword == null
                || !StringUtils.hasText(keyword.getKeyword())
                || keyword.getRiskLevel() == null
                || keyword.getRiskDetailsId() == null) {
            return;
        }

        String literal = keyword.getKeyword().trim();
        if (!StringUtils.hasText(literal)) {
            return;
        }

        RiskVocabularySnapshotItem item = RiskVocabularySnapshotItem.builder()
                .keywordId(keyword.getId())
                .riskDetailsId(keyword.getRiskDetailsId())
                .keyword(literal)
                .riskLevel(keyword.getRiskLevel())
                .build();
        dictionary.merge(literal, item, RiskVocabularyAcSupport::selectHigherPriorityItem);
    }

    /**
     * 快照词条进入 Trie 前的过滤和载荷转换逻辑。
     */
    private static void appendSnapshotItem(TreeMap<String, RiskTag> dictionary, RiskVocabularySnapshotItem item) {
        if (item == null
                || !StringUtils.hasText(item.getKeyword())
                || item.getRiskLevel() == null
                || item.getRiskDetailsId() == null) {
            return;
        }

        String literal = item.getKeyword().trim();
        if (!StringUtils.hasText(literal)) {
            return;
        }

        RiskTag riskTag = new RiskTag(item.getKeywordId(), item.getRiskDetailsId(), item.getRiskLevel());
        dictionary.merge(literal, riskTag, RiskVocabularyAcSupport::selectHigherPriorityTag);
    }

    /**
     * 同一个字面量特征词出现多条配置时，选择风险等级更高的一条。
     *
     * <p>风险等级数字越小优先级越高；等级相同时按 ID 做稳定兜底，避免 DB 顺序影响快照 hash。</p>
     */
    private static RiskVocabularySnapshotItem selectHigherPriorityItem(RiskVocabularySnapshotItem existing,
                                                                       RiskVocabularySnapshotItem incoming) {
        if (existing == null) {
            return incoming;
        }
        if (incoming == null || incoming.getRiskLevel() == null) {
            return existing;
        }
        if (existing.getRiskLevel() == null) {
            return incoming;
        }
        int riskLevelCompare = incoming.getRiskLevel().compareTo(existing.getRiskLevel());
        if (riskLevelCompare != 0) {
            return riskLevelCompare < 0 ? incoming : existing;
        }
        int keywordIdCompare = compareNullableLong(incoming.getKeywordId(), existing.getKeywordId());
        if (keywordIdCompare != 0) {
            return keywordIdCompare < 0 ? incoming : existing;
        }
        return compareNullableLong(incoming.getRiskDetailsId(), existing.getRiskDetailsId()) < 0 ? incoming : existing;
    }

    /**
     * Trie 载荷层面的重复关键词选择逻辑，和快照层保持一致。
     */
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
        int riskLevelCompare = incoming.getRiskLevel().compareTo(existing.getRiskLevel());
        if (riskLevelCompare != 0) {
            return riskLevelCompare < 0 ? incoming : existing;
        }
        int keywordIdCompare = compareNullableLong(incoming.getKeywordId(), existing.getKeywordId());
        if (keywordIdCompare != 0) {
            return keywordIdCompare < 0 ? incoming : existing;
        }
        return compareNullableLong(incoming.getRiskDetailsId(), existing.getRiskDetailsId()) < 0 ? incoming : existing;
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 空 ID 排在非空 ID 后面，用于重复词的稳定 tie-break。
     */
    private static int compareNullableLong(Long left, Long right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                String item = Integer.toHexString(value & 0xff);
                if (item.length() == 1) {
                    hex.append('0');
                }
                hex.append(item);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256 摘要算法", ex);
        }
    }
}
