package com.kant.llm.eval.knowledge.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentVersionDO;
import com.kant.llm.eval.knowledge.dao.mapper.KnowledgeFeatureDocumentVersionMapper;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class KnowledgeFeatureDocumentVersionServiceImpl
        extends ServiceImpl<KnowledgeFeatureDocumentVersionMapper, KnowledgeFeatureDocumentVersionDO>
        implements KnowledgeFeatureDocumentVersionService {

    @Override
    public void updateCurrentVersion(Long riskDetailsId, Long versionId) {

        KnowledgeFeatureDocumentVersionDO documentVersionDO = getOne(new LambdaQueryWrapper<KnowledgeFeatureDocumentVersionDO>()
                .eq(KnowledgeFeatureDocumentVersionDO::getRiskDetailsId, riskDetailsId));
        if (documentVersionDO == null) {
            documentVersionDO = new KnowledgeFeatureDocumentVersionDO();
            documentVersionDO.setId(IdUtil.getSnowflakeNextId());
            documentVersionDO.setRiskDetailsId(riskDetailsId);
            documentVersionDO.setCurrentVersionId(versionId);
            documentVersionDO.setCreateTime(LocalDateTime.now());
            documentVersionDO.setUpdateTime(LocalDateTime.now());
            documentVersionDO.setCreator("admin");
            documentVersionDO.setUpdater("admin");
            documentVersionDO.setStatus("VECTOR_STORED");
            save(documentVersionDO);
        } else {
            documentVersionDO.setCurrentVersionId(versionId);
            updateById(documentVersionDO);
        }
    }
}
