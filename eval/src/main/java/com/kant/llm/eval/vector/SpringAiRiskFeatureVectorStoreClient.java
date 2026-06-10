package com.kant.llm.eval.vector;

import com.kant.llm.eval.common.errorcode.BaseErrorCode;
import com.kant.llm.eval.common.exception.ServiceException;
import com.kant.llm.eval.dao.entity.RiskAttackFeatureDO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spring AI based risk feature vector store client.
 */
@Component
public class SpringAiRiskFeatureVectorStoreClient implements RiskFeatureVectorStoreClient {

    private static final UUID DOCUMENT_ID_NAMESPACE = UUID.fromString("74151d34-b88a-49d5-9aa8-f04239b8d3a6");

    private final VectorStore vectorStore;

    public SpringAiRiskFeatureVectorStoreClient(@Lazy @Qualifier("ragVectorStore") VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<RiskFeatureVectorHit> similaritySearch(String queryText, int topK) {
        if (StringUtils.isBlank(queryText)) {
            throw new ServiceException("Vector search query text must not be blank", BaseErrorCode.CLIENT_ERROR);
        }
        int limit = topK <= 0 ? 10 : topK;
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(queryText)
                .topK(limit)
                .build());
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream()
                .map(this::toHit)
                .toList();
    }

    @Override
    public void upsertFeature(RiskAttackFeatureDO feature) {
        validateFeature(feature);
        String documentId = buildDocumentId(feature.getId());
        vectorStore.delete(List.of(documentId));
        vectorStore.add(List.of(new Document(documentId, resolveContent(feature), buildMetadata(feature))));
    }

    @Override
    public void deleteFeature(Long featureId) {
        if (featureId == null) {
            throw new ServiceException("Risk feature id must not be null", BaseErrorCode.CLIENT_ERROR);
        }
        vectorStore.delete(List.of(buildDocumentId(featureId)));
    }

    private RiskFeatureVectorHit toHit(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return RiskFeatureVectorHit.builder()
                .featureId(toLong(metadata.get("featureId")))
                .riskDetailsId(toLong(metadata.get("riskDetailsId")))
                .categoryId(toLong(metadata.get("categoryId")))
                .featureText(document.getText())
                .featureType(toString(metadata.get("featureType")))
                .polarity(toString(metadata.get("polarity")))
                .riskLevel(toInteger(metadata.get("riskLevel")))
                .weight(toBigDecimal(metadata.get("weight")))
                .score(document.getScore())
                .metadata(metadata)
                .build();
    }

    private void validateFeature(RiskAttackFeatureDO feature) {
        if (feature == null || feature.getId() == null) {
            throw new ServiceException("Risk feature and id must not be null", BaseErrorCode.CLIENT_ERROR);
        }
        if (StringUtils.isBlank(resolveContent(feature))) {
            throw new ServiceException("Risk feature text must not be blank", BaseErrorCode.CLIENT_ERROR);
        }
    }

    private String resolveContent(RiskAttackFeatureDO feature) {
        return StringUtils.defaultIfBlank(feature.getNormalizedText(), feature.getFeatureText());
    }

    private Map<String, Object> buildMetadata(RiskAttackFeatureDO feature) {
        Map<String, Object> metadata = new HashMap<>();
        putString(metadata, "featureId", feature.getId());
        putString(metadata, "riskDetailsId", feature.getRiskDetailsId());
        putString(metadata, "categoryId", feature.getCategoryId());
        putString(metadata, "featureCode", feature.getFeatureCode());
        putString(metadata, "featureType", feature.getFeatureType());
        putString(metadata, "polarity", feature.getPolarity());
        putValue(metadata, "riskLevel", feature.getRiskLevel());
        putString(metadata, "language", feature.getLanguage());
        putString(metadata, "tags", feature.getTags());
        putString(metadata, "source", feature.getSource());
        putString(metadata, "weight", feature.getWeight());
        putString(metadata, "contentHash", feature.getContentHash());
        putValue(metadata, "version", feature.getVersion());
        return metadata;
    }

    private void putString(Map<String, Object> metadata, String key, Object value) {
        if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
            metadata.put(key, String.valueOf(value));
        }
    }

    private void putValue(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private String buildDocumentId(Long featureId) {
        return UUID.nameUUIDFromBytes((DOCUMENT_ID_NAMESPACE + ":" + featureId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && StringUtils.isNotBlank(string)) {
            return Long.valueOf(string);
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && StringUtils.isNotBlank(string)) {
            return Integer.valueOf(string);
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String string && StringUtils.isNotBlank(string)) {
            return new BigDecimal(string);
        }
        return null;
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
