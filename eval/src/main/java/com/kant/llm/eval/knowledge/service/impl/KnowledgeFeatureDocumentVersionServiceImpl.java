package com.kant.llm.eval.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentVersionDO;
import com.kant.llm.eval.knowledge.dao.mapper.KnowledgeFeatureDocumentVersionMapper;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KnowledgeFeatureDocumentVersionServiceImpl
        extends ServiceImpl<KnowledgeFeatureDocumentVersionMapper, KnowledgeFeatureDocumentVersionDO>
        implements KnowledgeFeatureDocumentVersionService {
}
