package com.kant.llm.eval.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentVersionSnapshotDO;
import com.kant.llm.eval.knowledge.dao.mapper.KnowledgeFeatureDocumentVersionSnapshotMapper;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentVersionSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KnowledgeFeatureDocumentVersionSnapshotServiceImpl
        extends ServiceImpl<KnowledgeFeatureDocumentVersionSnapshotMapper, KnowledgeFeatureDocumentVersionSnapshotDO>
        implements KnowledgeFeatureDocumentVersionSnapshotService {
}
