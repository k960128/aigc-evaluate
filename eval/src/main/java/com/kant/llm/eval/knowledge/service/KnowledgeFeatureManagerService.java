package com.kant.llm.eval.knowledge.service;

import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVersionSnapshotVO;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVersionVO;

import java.util.List;

public interface KnowledgeFeatureManagerService {
    /**
     * 获取攻击语义知识库版本
     * @return 攻击语义知识库版本响应体
     */
    KnowledgeFeatureDocumentVersionVO getKnowledgeFeatureDocumentVersion();

    List<KnowledgeFeatureDocumentVersionSnapshotVO> getKnowledgeFeatureDocumentVersionSnapshotList();
}
