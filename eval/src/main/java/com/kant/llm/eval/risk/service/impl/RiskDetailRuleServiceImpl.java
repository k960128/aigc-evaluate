package com.kant.llm.eval.risk.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.risk.dao.entity.RiskDetailRuleDO;
import com.kant.llm.eval.risk.dao.mapper.RiskDetailRuleMapper;
import com.kant.llm.eval.risk.service.RiskDetailRuleService;
import org.springframework.stereotype.Service;

/**
 * 风险小类判定规则服务实现。
 */
@Service
public class RiskDetailRuleServiceImpl extends ServiceImpl<RiskDetailRuleMapper, RiskDetailRuleDO> implements RiskDetailRuleService {
}
