package com.kant.llm.eval.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeDocumentSampleRelationDO;
import com.kant.llm.eval.knowledge.dao.mapper.KnowledgeDocumentSampleRelationMapper;
import com.kant.llm.eval.knowledge.service.KnowledgeDocumentSampleRelationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KnowledgeDocumentSampleRelationServiceImpl
        extends ServiceImpl<KnowledgeDocumentSampleRelationMapper, KnowledgeDocumentSampleRelationDO>
        implements KnowledgeDocumentSampleRelationService {
}
