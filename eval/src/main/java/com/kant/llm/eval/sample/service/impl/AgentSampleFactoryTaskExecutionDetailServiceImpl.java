package com.kant.llm.eval.sample.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.sample.dao.entity.AgentSampleFactoryTaskExecutionDetailDO;
import com.kant.llm.eval.sample.dao.mapper.AgentSampleFactoryTaskExecutionDetailMapper;
import com.kant.llm.eval.sample.service.AgentSampleFactoryTaskExecutionDetailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AgentSampleFactoryTaskExecutionDetailServiceImpl extends ServiceImpl<AgentSampleFactoryTaskExecutionDetailMapper, AgentSampleFactoryTaskExecutionDetailDO> implements AgentSampleFactoryTaskExecutionDetailService {
}
