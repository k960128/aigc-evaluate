package com.kant.llm.eval.sample.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.sample.dao.entity.AgentSampleFactoryTaskDO;
import com.kant.llm.eval.sample.dao.mapper.AgentSampleFactoryTaskMapper;
import com.kant.llm.eval.sample.service.AgentSampleFactoryTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AgentSampleFactoryTaskServiceImpl extends ServiceImpl<AgentSampleFactoryTaskMapper, AgentSampleFactoryTaskDO> implements AgentSampleFactoryTaskService {
}
