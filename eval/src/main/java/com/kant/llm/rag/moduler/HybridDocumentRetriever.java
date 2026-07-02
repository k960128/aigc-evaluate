package com.kant.llm.rag.moduler;

import com.kant.llm.common.constant.EsDocumentChunk;
import com.kant.llm.eval.pipeline.service.es.ElasticSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 混合召回器。
 *
 * <p>职责边界：本类只负责“双路召回”，不做融合、不做重排序、不做 prompt 注入。</p>
 *
 * <p>当前召回链路：</p>
 * <ul>
 *     <li>ES：通过 {@link ElasticSearchService#searchRiskAttackFeatures(String, int)} 做关键词/文本召回。</li>
 *     <li>VectorStore：通过 {@link VectorStore#similaritySearch(SearchRequest)} 做语义召回。</li>
 * </ul>
 *
 * <p>为了让后续 {@link RrfDocumentJoiner} 能识别多路来源，本类会给每条 {@link Document}
 * 补充统一 metadata，例如 retrievalSource、sourceRank、sourceScore、stableId。</p>
 */
@Slf4j
public class HybridDocumentRetriever implements DocumentRetriever {

    /** ES 召回来源标识。 */
    public static final String SOURCE_ES = "ES";

    /** 向量库召回来源标识。 */
    public static final String SOURCE_VECTOR = "VECTOR";

    /** 文档来源：ES / VECTOR。 */
    public static final String METADATA_RETRIEVAL_SOURCE = "retrievalSource";

    /** 文档在当前召回源中的排名，从 1 开始。 */
    public static final String METADATA_SOURCE_RANK = "sourceRank";

    /** 当前召回源返回的原始分或归一化分。 */
    public static final String METADATA_SOURCE_SCORE = "sourceScore";

    /** 跨召回源稳定去重 ID，后续 RRF 会优先使用它做去重。 */
    public static final String METADATA_STABLE_ID = "stableId";

    /** 兼容 L2 知识特征 ID。 */
    public static final String METADATA_FEATURE_ID = "featureId";

    /** 兼容普通文档 ID。 */
    public static final String METADATA_DOCUMENT_ID = "documentId";

    /** 兼容文档切片 ID。 */
    public static final String METADATA_CHUNK_ID = "chunkId";

    private static final int DEFAULT_TOP_K = 5;

    private final ElasticSearchService elasticSearchService;

    private final VectorStore vectorStore;

    private final int esTopK;

    private final int vectorTopK;

    private final Double vectorSimilarityThreshold;

    public HybridDocumentRetriever(ElasticSearchService elasticSearchService, VectorStore vectorStore) {
        this(elasticSearchService, vectorStore, DEFAULT_TOP_K, DEFAULT_TOP_K, null);
    }

    public HybridDocumentRetriever(ElasticSearchService elasticSearchService, VectorStore vectorStore,
                                   int esTopK, int vectorTopK) {
        this(elasticSearchService, vectorStore, esTopK, vectorTopK, null);
    }

    public HybridDocumentRetriever(ElasticSearchService elasticSearchService, VectorStore vectorStore,
                                   int esTopK, int vectorTopK, Double vectorSimilarityThreshold) {
        this.elasticSearchService = elasticSearchService;
        this.vectorStore = vectorStore;
        this.esTopK = resolveTopK(esTopK);
        this.vectorTopK = resolveTopK(vectorTopK);
        this.vectorSimilarityThreshold = vectorSimilarityThreshold;
    }

    @Override
    public List<Document> retrieve(Query query) {
        if (query == null || !StringUtils.hasText(query.text())) {
            return List.of();
        }

        // 只做双路召回和来源标记，结果融合交给 RrfDocumentJoiner。
        List<Document> documents = new ArrayList<>();
        documents.addAll(retrieveFromEs(query.text()));
        documents.addAll(retrieveFromVectorStore(query.text()));
        log.info("Hybrid RAG recall finished, query: {}, esTopK: {}, vectorTopK: {}, totalDocuments: {}",
                query.text(), esTopK, vectorTopK, documents.size());
        return documents;
    }

    /**
     * 执行 ES 召回。
     *
     * <p>ES 异常不会中断整个 RAG 流程，失败时返回空列表，让向量召回继续工作。</p>
     */
    private List<Document> retrieveFromEs(String queryText) {
        if (elasticSearchService == null) {
            return List.of();
        }
        try {
            List<EsDocumentChunk> hits = elasticSearchService.searchRiskAttackFeatures(queryText, esTopK);
            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < hits.size(); i++) {
                Document document = toEsDocument(hits.get(i), i + 1);
                if (document != null) {
                    documents.add(document);
                }
            }
            log.info("Hybrid RAG ES recall finished, queryLength: {}, hits: {}", queryText.length(), documents.size());
            return documents;
        } catch (Exception ex) {
            log.warn("Hybrid RAG ES recall failed, queryLength: {}, reason: {}", queryText.length(), ex.getMessage(), ex);
            return List.of();
        }
    }

    /**
     * 将 ES 业务文档转换成 Spring AI Document。
     *
     * <p>这里会保留 L2 知识库常用 metadata，同时补充 RAG 融合所需的 source/rank/score。</p>
     */
    private Document toEsDocument(EsDocumentChunk hit, int rank) {
        if (hit == null) {
            return null;
        }
        String text = hit.getContent();
        if (!StringUtils.hasText(text)) {
            return null;
        }

        String stableId = firstText(
                hit.getFeatureId() == null ? null : String.valueOf(hit.getFeatureId()),
                hit.getId(),
                hit.getContentHash());
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (hit.getMetadata() != null) {
            metadata.putAll(hit.getMetadata());
        }
        putIfPresent(metadata, METADATA_FEATURE_ID, hit.getFeatureId());
        putIfPresent(metadata, "riskDetailsId", hit.getRiskDetailsId());
        putIfPresent(metadata, "categoryId", hit.getCategoryId());
        putIfPresent(metadata, "featureCode", hit.getFeatureCode());
        putIfPresent(metadata, "featureType", hit.getFeatureType());
        putIfPresent(metadata, "polarity", hit.getPolarity());
        putIfPresent(metadata, "riskLevel", hit.getRiskLevel());
        putIfPresent(metadata, "contentHash", hit.getContentHash());

        return buildDocument(stableId, text, metadata, SOURCE_ES, rank, toDouble(hit.getNormalizedScore(), hit.getScore()));
    }

    /**
     * 执行向量库召回。
     *
     * <p>可通过构造参数传入 similarityThreshold；未传时使用 VectorStore 默认行为。</p>
     */
    private List<Document> retrieveFromVectorStore(String queryText) {
        if (vectorStore == null) {
            return List.of();
        }
        try {
            SearchRequest.Builder requestBuilder = SearchRequest.builder()
                    .query(queryText)
                    .topK(vectorTopK);
            if (vectorSimilarityThreshold != null) {
                requestBuilder.similarityThreshold(vectorSimilarityThreshold);
            }
            List<Document> hits = vectorStore.similaritySearch(requestBuilder.build());
            if (hits == null || hits.isEmpty()) {
                return List.of();
            }
            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < hits.size(); i++) {
                Document document = enrichVectorDocument(hits.get(i), i + 1);
                if (document != null) {
                    documents.add(document);
                }
            }
            log.info("Hybrid RAG Vector recall finished, queryLength: {}, hits: {}", queryText.length(), documents.size());
            return documents;
        } catch (Exception ex) {
            log.warn("Hybrid RAG Vector recall failed, queryLength: {}, reason: {}", queryText.length(), ex.getMessage(), ex);
            return List.of();
        }
    }

    /**
     * 给向量库返回的 Document 补充统一 metadata。
     */
    private Document enrichVectorDocument(Document document, int rank) {
        if (document == null || !StringUtils.hasText(document.getText())) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
        String stableId = resolveStableId(document);
        return buildDocument(stableId, document.getText(), metadata, SOURCE_VECTOR, rank, document.getScore());
    }

    /**
     * 构建带统一 metadata 的 Document。
     *
     * <p>注意：Spring AI Document 本身不可变，所以这里通过 builder 生成新的 Document。</p>
     */
    private Document buildDocument(String stableId, String text, Map<String, Object> metadata,
                                   String source, int rank, Double score) {
        String resolvedStableId = StringUtils.hasText(stableId) ? stableId : text;
        metadata.put(METADATA_STABLE_ID, resolvedStableId);
        metadata.put(METADATA_RETRIEVAL_SOURCE, source);
        metadata.put(METADATA_SOURCE_RANK, rank);
        if (score != null) {
            metadata.put(METADATA_SOURCE_SCORE, score);
        }

        Document.Builder builder = Document.builder()
                .id(source.toLowerCase() + ":" + resolvedStableId)
                .text(text)
                .metadata(metadata);
        if (score != null) {
            builder.score(score);
        }
        return builder.build();
    }

    /**
     * 解析跨来源稳定 ID。
     *
     * <p>优先级：stableId > featureId > documentId > chunkId > Document.id。</p>
     */
    private String resolveStableId(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return firstText(
                asString(metadata.get(METADATA_STABLE_ID)),
                asString(metadata.get(METADATA_FEATURE_ID)),
                asString(metadata.get(METADATA_DOCUMENT_ID)),
                asString(metadata.get(METADATA_CHUNK_ID)),
                document.getId());
    }

    private int resolveTopK(int topK) {
        return topK <= 0 ? DEFAULT_TOP_K : topK;
    }

    private Double toDouble(BigDecimal value, Double fallback) {
        if (value != null) {
            return value.doubleValue();
        }
        return fallback;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
