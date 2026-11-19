package com.kant.llm.eval.knowledge.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentVersionDO;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureCurrentVersionVO;

public interface KnowledgeFeatureDocumentVersionService extends IService<KnowledgeFeatureDocumentVersionDO> {
    void updateCurrentVersion(Long currentVersionId);

    /**
     * 从缓存中获取当前激活版本ID
     * @return 版本ID
     */
    KnowledgeFeatureCurrentVersionVO getCurrentVersionCache();
}
