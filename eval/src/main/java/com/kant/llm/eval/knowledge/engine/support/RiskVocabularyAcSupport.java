package com.kant.llm.eval.knowledge.engine.support;

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeVocabularyKeywordDO;
import com.kant.llm.eval.knowledge.engine.model.RiskTag;
import com.kant.llm.eval.knowledge.engine.model.RiskVocabularySnapshotItem;
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
 * AC 快照词条转换、哈希计算和字典树构建的公共规则。
 */
public final class RiskVocabularyAcSupport {

    private RiskVocabularyAcSupport() {
    }

    public static List<RiskVocabularySnapshotItem> toSnapshotItems(List<KnowledgeVocabularyKeywordDO> keywords) {
        TreeMap<String, RiskVocabularySnapshotItem> dictionary = new TreeMap<>();
        if (!CollectionUtils.isEmpty(keywords)) {
            for (KnowledgeVocabularyKeywordDO keyword : keywords) {
                appendKeyword(dictionary, keyword);
            }
        }
        return new ArrayList<>(dictionary.values());
    }

    public static String calculateHash(List<RiskVocabularySnapshotItem> items) {
        MessageDigest digest = newSha256Digest();
        if (!CollectionUtils.isEmpty(items)) {
            items.stream()
                    .sorted(Comparator.comparing(RiskVocabularySnapshotItem::getKeyword,
                            Comparator.nullsFirst(String::compareTo)))
                    .forEach(item -> updateDigest(digest, item));
        }
        return toHex(digest.digest());
    }

    public static void updateDigest(MessageDigest digest, RiskVocabularySnapshotItem item) {
        updateDigest(digest, nullToEmpty(item.getKeyword()));
        updateDigest(digest, "|");
        updateDigest(digest, nullToEmpty(item.getKeywordId()));
        updateDigest(digest, "|");
        updateDigest(digest, nullToEmpty(item.getRiskDetailsId()));
        updateDigest(digest, "|");
        updateDigest(digest, nullToEmpty(item.getRiskLevel()));
        updateDigest(digest, "\n");
    }

    public static void updateDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    public static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("\u5f53\u524d\u8fd0\u884c\u73af\u5883\u4e0d\u652f\u6301 SHA-256 \u6458\u8981\u7b97\u6cd5", ex);
        }
    }

    public static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            String item = Integer.toHexString(value & 0xff);
            if (item.length() == 1) {
                hex.append('0');
            }
            hex.append(item);
        }
        return hex.toString();
    }

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

    private static void appendKeyword(TreeMap<String, RiskVocabularySnapshotItem> dictionary,
                                      KnowledgeVocabularyKeywordDO keyword) {
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
}
