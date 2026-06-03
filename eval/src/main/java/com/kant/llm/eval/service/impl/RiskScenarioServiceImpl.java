package com.kant.llm.eval.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.dao.entity.RiskScenarioDO;
import com.kant.llm.eval.dao.mapper.RiskScenarioMapper;
import com.kant.llm.eval.service.RiskScenarioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RiskScenarioServiceImpl extends ServiceImpl<RiskScenarioMapper, RiskScenarioDO> implements RiskScenarioService {
}