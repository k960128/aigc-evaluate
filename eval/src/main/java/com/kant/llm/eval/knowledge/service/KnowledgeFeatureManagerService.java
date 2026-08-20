package com.kant.llm.eval.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.llm.eval.knowledge.dto.req.KnowledgeFeatureDocumentPageReq;
import com.kant.llm.eval.knowledge.dto.req.KnowledgeFeatureDocumentReq;
import com.kant.llm.eval.knowledge.dto.req.KnowledgeFeatureVersionReq;
import com.kant.llm.eval.knowledge.dto.req.PublishVersionReq;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVO;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVersionSnapshotVO;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVersionVO;

import java.util.List;

public interface KnowledgeFeatureManagerService {
    /**
     * 获取攻击语义知识库版本
     * @return 攻击语义知识库版本响应体
     */
    KnowledgeFeatureDocumentVersionVO getKnowledgeFeatureDocumentVersion(KnowledgeFeatureVersionReq req);

    /**
     * 获取攻击语义知识库版本快照列表
     * @param req 风险小类ID请求体
     * @return 攻击语义知识库版本快照列表响应体
     */
    List<KnowledgeFeatureDocumentVersionSnapshotVO> getKnowledgeFeatureDocumentVersionSnapshotList(KnowledgeFeatureVersionReq req);

    /**
     * 获取攻击语义知识库特征文档分页列表
     * @param pageReq 分页请求体
     * @return 攻击语义知识库特征文档分页列表响应体
     */
    Page<KnowledgeFeatureDocumentVO> getKnowledgeFeatureDocumentPage(KnowledgeFeatureDocumentPageReq pageReq);

    /**
     * 保存攻击语义知识库特征文档
     * @param req 请求体
     * @return 结果
     */
    Boolean saveKnowledgeFeatureDocument(KnowledgeFeatureDocumentReq req);

    /**
     * 发布版本
     * @param req 请求体
     * @return 结果 结果
     */
    Boolean publishVersion(PublishVersionReq req);
}
