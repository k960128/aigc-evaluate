package com.kant.llm.eval.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentDO;
import com.kant.llm.eval.knowledge.dao.mapper.KnowledgeFeatureDocumentMapper;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class KnowledgeFeatureDocumentServiceImpl
        extends ServiceImpl<KnowledgeFeatureDocumentMapper, KnowledgeFeatureDocumentDO>
        implements KnowledgeFeatureDocumentService {
    
    @Override
    public List<KnowledgeFeatureDocumentDO> listByRiskDetailsIdAndVersionId(Long riskDetailsId, Long versionId) {
        return list(new LambdaQueryWrapper<KnowledgeFeatureDocumentDO>()
                .and(w -> w.eq(KnowledgeFeatureDocumentDO::getRiskDetailsId, riskDetailsId)
                        .eq(KnowledgeFeatureDocumentDO::getCurrentVersionId, versionId))
                .or(w -> w.isNull(KnowledgeFeatureDocumentDO::getCurrentVersion)
                        .isNull(KnowledgeFeatureDocumentDO::getCurrentVersionId)));
    }
}
