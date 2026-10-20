package com.kant.llm.eval.sample.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.sample.dao.entity.AgentSampleMutationTacticsDO;
import com.kant.llm.eval.sample.dao.mapper.AgentSampleMutationTacticsMapper;
import com.kant.llm.eval.sample.service.AgentSampleMutationTacticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AgentSampleMutationTacticsServiceImpl extends ServiceImpl<AgentSampleMutationTacticsMapper, AgentSampleMutationTacticsDO> implements AgentSampleMutationTacticsService {
}
