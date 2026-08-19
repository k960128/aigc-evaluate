package com.kant.llm.eval.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentDO;
import com.kant.llm.eval.knowledge.dao.mapper.KnowledgeFeatureDocumentMapper;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KnowledgeFeatureDocumentServiceImpl
        extends ServiceImpl<KnowledgeFeatureDocumentMapper, KnowledgeFeatureDocumentDO>
        implements KnowledgeFeatureDocumentService {
}
