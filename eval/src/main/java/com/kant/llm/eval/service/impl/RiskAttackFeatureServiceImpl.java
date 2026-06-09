package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.RiskAttackFeatureDO;
import com.kant.llm.eval.dao.mapper.RiskAttackFeatureMapper;
import com.kant.llm.eval.service.RiskAttackFeatureService;
import org.springframework.stereotype.Service;

/**
 * L2 攻击特征知识服务实现。
 */
@Service
public class RiskAttackFeatureServiceImpl extends ServiceImpl<RiskAttackFeatureMapper, RiskAttackFeatureDO> implements RiskAttackFeatureService {
}
