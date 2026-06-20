package com.kant.llm.eval.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeAttackFeatureDO;
import com.kant.llm.eval.knowledge.dao.mapper.KnowledgeAttackFeatureMapper;
import com.kant.llm.eval.knowledge.service.KnowledgeAttackFeatureService;
import org.springframework.stereotype.Service;

/**
 * L2 攻击特征知识服务实现。
 */
@Service
public class KnowledgeAttackFeatureServiceImpl extends ServiceImpl<KnowledgeAttackFeatureMapper, KnowledgeAttackFeatureDO> implements KnowledgeAttackFeatureService {
}
