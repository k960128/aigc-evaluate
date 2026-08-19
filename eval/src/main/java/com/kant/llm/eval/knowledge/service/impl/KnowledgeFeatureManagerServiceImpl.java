package com.kant.llm.eval.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentVersionDO;
import com.kant.llm.eval.knowledge.dao.mapper.KnowledgeFeatureDocumentVersionMapper;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVersionSnapshotVO;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVersionVO;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentService;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentVersionService;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentVersionSnapshotService;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 攻击语义知识库聚合管理Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeFeatureManagerServiceImpl implements KnowledgeFeatureManagerService {

    // ======================================
    private final KnowledgeFeatureDocumentService knowledgeFeatureDocumentService;
    private final KnowledgeFeatureDocumentVersionService knowledgeFeatureDocumentVersionService;
    private final KnowledgeFeatureDocumentVersionSnapshotService knowledgeFeatureDocumentVersionSnapshotService;
    // ======================================

    @Override
    public KnowledgeFeatureDocumentVersionVO getKnowledgeFeatureDocumentVersion() {
        KnowledgeFeatureDocumentVersionDO knowledgeFeatureDocumentVersionDO = knowledgeFeatureDocumentVersionService.getOne(null);
        if (knowledgeFeatureDocumentVersionDO == null) {
            return null;
        }
        return BeanUtil.copyProperties(knowledgeFeatureDocumentVersionDO, KnowledgeFeatureDocumentVersionVO.class);
    }

    @Override
    public List<KnowledgeFeatureDocumentVersionSnapshotVO> getKnowledgeFeatureDocumentVersionSnapshotList() {
        return List.of();
    }
}
