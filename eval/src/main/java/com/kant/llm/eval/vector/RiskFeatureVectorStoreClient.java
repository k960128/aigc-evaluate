package com.kant.llm.eval.vector;

import com.kant.llm.eval.dao.entity.RiskAttackFeatureDO;

import java.util.List;

/**
 * Risk feature vector store client.
 */
public interface RiskFeatureVectorStoreClient {

    List<RiskFeatureVectorHit> similaritySearch(String queryText, int topK);

    void upsertFeature(RiskAttackFeatureDO feature);

    void deleteFeature(Long featureId);
}
