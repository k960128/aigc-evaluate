package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.RiskDetailRuleDO;
import com.kant.llm.eval.dao.mapper.RiskDetailRuleMapper;
import com.kant.llm.eval.service.RiskDetailRuleService;
import org.springframework.stereotype.Service;

/**
 * 风险小类判定规则服务实现。
 */
@Service
public class RiskDetailRuleServiceImpl extends ServiceImpl<RiskDetailRuleMapper, RiskDetailRuleDO> implements RiskDetailRuleService {
}
