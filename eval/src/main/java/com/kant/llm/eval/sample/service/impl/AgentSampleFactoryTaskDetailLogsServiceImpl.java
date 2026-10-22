package com.kant.llm.eval.sample.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.sample.dao.entity.AgentSampleFactoryTaskDetailLogsDO;
import com.kant.llm.eval.sample.dao.mapper.AgentSampleFactoryTaskDetailLogsMapper;
import com.kant.llm.eval.sample.service.AgentSampleFactoryTaskDetailLogsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 样本扩充-任务详情执行批次详情日志 Service 实现
 */
@Slf4j
@Service
public class AgentSampleFactoryTaskDetailLogsServiceImpl extends ServiceImpl<AgentSampleFactoryTaskDetailLogsMapper, AgentSampleFactoryTaskDetailLogsDO> implements AgentSampleFactoryTaskDetailLogsService {
}
