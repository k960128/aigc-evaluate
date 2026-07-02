package com.kant.llm.rag.moduler;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 RRF（Reciprocal Rank Fusion，倒数秩融合）的文档合并器。
 *
 * <p>职责边界：本类只负责“多路召回结果融合”，不负责召回，也不调用重排序模型。</p>
 *
 * <p>它依赖 {@link HybridDocumentRetriever} 在 Document metadata 中写入的：</p>
 * <ul>
 *     <li>stableId / featureId / documentId / chunkId：用于跨来源去重。</li>
 *     <li>sourceRank：用于计算 RRF 分。</li>
 *     <li>retrievalSource：用于记录最终文档来自哪些召回源。</li>
 * </ul>
 *
 * <p>RRF 公式：1 / (k + rank)。同一文档被多个来源召回时，分数会累加。</p>
 */
public class RrfDocumentJoiner implements DocumentJoiner {

    /** RRF 融合后的分数。 */
    public static final String METADATA_RRF_SCORE = "rrfScore";

    /** 文档命中的召回来源集合，例如 ES、VECTOR。 */
    public static final String METADATA_RRF_SOURCES = "rrfSources";

    /** RRF 平滑参数，沿用 L2 流水线默认值。 */
    private static final int DEFAULT_RRF_K = 60;

    /** RRF 后默认保留候选数。 */
    private static final int DEFAULT_TOP_N = 20;

    private final int rrfK;

    private final int topN;

    public RrfDocumentJoiner() {
        this(DEFAULT_RRF_K, DEFAULT_TOP_N);
    }

    public RrfDocumentJoiner(int rrfK, int topN) {
        this.rrfK = rrfK <= 0 ? DEFAULT_RRF_K : rrfK;
        this.topN = topN <= 0 ? DEFAULT_TOP_N : topN;
    }

    @Override
    public List<Document> join(Map<Query, List<List<Document>>> documentsForQuery) {
        if (documentsForQuery == null || documentsForQuery.isEmpty()) {
            return List.of();
        }

        // 使用 LinkedHashMap 保持同分时的首次出现顺序，减少排序结果抖动。
        Map<String, RrfCandidate> candidateMap = new LinkedHashMap<>();
        for (List<List<Document>> retrieverResults : documentsForQuery.values()) {
            if (retrieverResults == null) {
                continue;
            }
            for (List<Document> documents : retrieverResults) {
                mergeDocuments(candidateMap, documents);
            }
        }

        return candidateMap.values().stream()
                .sorted(Comparator.comparing(RrfCandidate::rrfScore).reversed())
                .limit(topN)
                .map(RrfCandidate::toDocument)
                .toList();
    }

    /**
     * 将单批召回结果合并到候选池。
     */
    private void mergeDocuments(Map<String, RrfCandidate> candidateMap, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            if (document == null) {
                continue;
            }
            String stableId = resolveStableId(document);
            int rank = resolveRank(document, i + 1);
            String source = resolveSource(document);
            // 标准 RRF 分数：只依赖排名，不依赖 ES/向量库原始分数的量纲。
            double score = 1.0D / (rrfK + rank);

            RrfCandidate candidate = candidateMap.computeIfAbsent(stableId, ignored -> new RrfCandidate(document));
            candidate.addScore(score, source, document);
        }
    }

    /**
     * 解析去重 ID。
     *
     * <p>优先使用 HybridDocumentRetriever 写入的 stableId，其次兼容常见业务 ID。</p>
     */
    private String resolveStableId(Document document) {
        Object stableId = document.getMetadata().get(HybridDocumentRetriever.METADATA_STABLE_ID);
        if (stableId != null && StringUtils.hasText(String.valueOf(stableId))) {
            return String.valueOf(stableId);
        }
        Object featureId = document.getMetadata().get(HybridDocumentRetriever.METADATA_FEATURE_ID);
        if (featureId != null && StringUtils.hasText(String.valueOf(featureId))) {
            return String.valueOf(featureId);
        }
        Object documentId = document.getMetadata().get(HybridDocumentRetriever.METADATA_DOCUMENT_ID);
        if (documentId != null && StringUtils.hasText(String.valueOf(documentId))) {
            return String.valueOf(documentId);
        }
        Object chunkId = document.getMetadata().get(HybridDocumentRetriever.METADATA_CHUNK_ID);
        if (chunkId != null && StringUtils.hasText(String.valueOf(chunkId))) {
            return String.valueOf(chunkId);
        }
        return document.getId();
    }

    /**
     * 解析当前文档在原召回源中的排名。
     */
    private int resolveRank(Document document, int fallbackRank) {
        Object rank = document.getMetadata().get(HybridDocumentRetriever.METADATA_SOURCE_RANK);
        if (rank instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        try {
            return rank == null ? fallbackRank : Math.max(1, Integer.parseInt(String.valueOf(rank)));
        } catch (NumberFormatException ex) {
            return fallbackRank;
        }
    }

    /**
     * 解析召回来源，用于最终 metadata 追踪。
     */
    private String resolveSource(Document document) {
        Object source = document.getMetadata().get(HybridDocumentRetriever.METADATA_RETRIEVAL_SOURCE);
        return source == null ? "UNKNOWN" : String.valueOf(source);
    }

    /**
     * RRF 候选聚合对象。
     *
     * <p>同一个 stableId 可能有多条 Document，本对象负责累加 RRF 分，
     * 并保留原始 score 更高的那条 Document 作为最终文本载体。</p>
     */
    private static final class RrfCandidate {

        private Document document;

        private double rrfScore;

        private final Set<String> sources = new LinkedHashSet<>();

        private RrfCandidate(Document document) {
            this.document = document;
        }

        /**
         * 累加单个召回来源贡献的 RRF 分数。
         */
        private void addScore(double score, String source, Document newDocument) {
            this.rrfScore += score;
            this.sources.add(source);
            if (isBetterDocument(newDocument, this.document)) {
                this.document = newDocument;
            }
        }

        private double rrfScore() {
            return rrfScore;
        }

        /**
         * 生成最终 Document，并把 RRF 分数和来源集合写回 metadata。
         */
        private Document toDocument() {
            Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
            metadata.put(METADATA_RRF_SCORE, rrfScore);
            metadata.put(METADATA_RRF_SOURCES, new ArrayList<>(sources));
            return document.mutate()
                    .metadata(metadata)
                    .score(rrfScore)
                    .build();
        }

        /**
         * 同一文档多来源命中时，保留原始召回分更高的文本载体。
         */
        private boolean isBetterDocument(Document left, Document right) {
            Double leftScore = left.getScore();
            Double rightScore = right.getScore();
            if (leftScore == null) {
                return false;
            }
            return rightScore == null || leftScore > rightScore;
        }
    }
}
