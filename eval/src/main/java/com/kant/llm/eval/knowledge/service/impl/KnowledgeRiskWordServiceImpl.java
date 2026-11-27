package com.kant.llm.eval.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeRiskWordDO;
import com.kant.llm.eval.knowledge.dao.mapper.KnowledgeRiskWordMapper;
import com.kant.llm.eval.knowledge.service.KnowledgeRiskWordService;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeRiskWordServiceImpl
        extends ServiceImpl<KnowledgeRiskWordMapper, KnowledgeRiskWordDO>
        implements KnowledgeRiskWordService {
}
