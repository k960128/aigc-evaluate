package com.kant.llm.eval.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentDO;

import java.util.List;

public interface KnowledgeFeatureDocumentService extends IService<KnowledgeFeatureDocumentDO> {

    /**
     * 根据风险详情ID和版本ID查询知识特征文档列表
     * @param riskDetailsId 风险详情ID
     * @param versionId 版本ID
     * @return 知识特征档列表
     */
    List<KnowledgeFeatureDocumentDO> listByRiskDetailsIdAndVersionId(Long riskDetailsId, Long versionId);
}
