package com.kant.llm.eval.sample.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.framework.exception.ServiceException;
import com.kant.llm.eval.sample.dao.entity.AgentSampleFactoryTaskDetailLogsDO;
import com.kant.llm.eval.sample.dao.mapper.AgentSampleFactoryTaskDetailLogsMapper;
import com.kant.llm.eval.sample.dto.SampleFactoryTaskDetailLogsVO;
import com.kant.llm.eval.sample.service.AgentSampleFactoryTaskDetailLogsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 样本扩充-任务详情执行批次详情日志 Service 实现
 */
@Slf4j
@Service
public class AgentSampleFactoryTaskDetailLogsServiceImpl extends ServiceImpl<AgentSampleFactoryTaskDetailLogsMapper, AgentSampleFactoryTaskDetailLogsDO> implements AgentSampleFactoryTaskDetailLogsService {

    @Override
    public List<SampleFactoryTaskDetailLogsVO> listSampleFactoryTaskDetailLogs(Long taskDetailId) {
        return list(Wrappers.<AgentSampleFactoryTaskDetailLogsDO>lambdaQuery()
                .eq(AgentSampleFactoryTaskDetailLogsDO::getTaskDetailId, taskDetailId)
                .orderByAsc(AgentSampleFactoryTaskDetailLogsDO::getCreateTime)
                .orderByAsc(AgentSampleFactoryTaskDetailLogsDO::getId))
                .stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    public void startExecution(String taskId, Long taskDetailId, int currentRound, String messageId, String type, String promptContent) {
        AgentSampleFactoryTaskDetailLogsDO executionLog = new AgentSampleFactoryTaskDetailLogsDO();
        executionLog.setTaskId(taskId);
        executionLog.setTaskDetailId(taskDetailId);
        executionLog.setMessageId(messageId);
        executionLog.setCurrentRound(currentRound);
        executionLog.setType(type);
        executionLog.setPromptContent(promptContent);
        executionLog.setStatus("RUNNING");
        executionLog.setLockVersion(0);
        executionLog.setDeleted(false);
        if (!save(executionLog)) {
            throw new ServiceException("创建Agent执行日志失败");
        }
    }

    @Override
    public void completeExecution(String messageId, String contentResponse, Integer tokenCount, int latency, boolean success) {
        AgentSampleFactoryTaskDetailLogsDO executionLog = getOne(
                Wrappers.<AgentSampleFactoryTaskDetailLogsDO>lambdaQuery()
                        .select(AgentSampleFactoryTaskDetailLogsDO::getId,
                                AgentSampleFactoryTaskDetailLogsDO::getLockVersion)
                        .eq(AgentSampleFactoryTaskDetailLogsDO::getMessageId, messageId)
                        .eq(AgentSampleFactoryTaskDetailLogsDO::getStatus, "RUNNING"));
        if (executionLog == null) {
            throw new ServiceException("Agent执行日志不存在或已结束");
        }
        executionLog.setContentResponse(contentResponse);
        executionLog.setTokenCount(tokenCount);
        executionLog.setLatency(latency);
        executionLog.setStatus(success ? "SUCCESS" : "FAILED");
        executionLog.setUpdateTime(LocalDateTime.now());
        if (!updateById(executionLog)) {
            throw new ServiceException("更新Agent执行日志失败，日志可能已被修改");
        }
    }

    private SampleFactoryTaskDetailLogsVO convertToVO(AgentSampleFactoryTaskDetailLogsDO log) {
        SampleFactoryTaskDetailLogsVO vo = new SampleFactoryTaskDetailLogsVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }
}
