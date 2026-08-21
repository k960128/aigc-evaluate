package com.kant.llm.eval.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentVersionDO;

public interface KnowledgeFeatureDocumentVersionService extends IService<KnowledgeFeatureDocumentVersionDO> {
    void updateCurrentVersion(Long riskDetailsId, Long versionId);
}
