package com.kant.llm.eval.sample.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.agent.simple.AgentExecutionListener;
import com.kant.agent.simple.SimpleOrchestrator;
import com.kant.agent.simple.model.SampleTaskTicket;
import com.kant.framework.exception.ServiceException;
import com.kant.llm.eval.sample.dao.entity.AgentSampleFactoryTaskDO;
import com.kant.llm.eval.sample.dao.entity.AgentSampleFactoryTaskExecutionDetailDO;
import com.kant.llm.eval.sample.dao.entity.AgentSampleMutationTacticsDO;
import com.kant.llm.eval.sample.dao.mapper.AgentSampleFactoryTaskMapper;
import com.kant.llm.eval.sample.dao.mapper.AgentSampleMutationTacticsMapper;
import com.kant.llm.eval.sample.dto.SampleFactoryTaskPageRequest;
import com.kant.llm.eval.sample.dto.SampleFactoryTaskVO;
import com.kant.llm.eval.sample.dto.SeedCreateTask;
import com.kant.llm.eval.sample.service.AgentSampleFactoryTaskDetailLogsService;
import com.kant.llm.eval.sample.service.AgentSampleFactoryTaskExecutionDetailService;
import com.kant.llm.eval.sample.service.AgentSampleFactoryTaskService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSampleFactoryTaskServiceImpl extends ServiceImpl<AgentSampleFactoryTaskMapper, AgentSampleFactoryTaskDO> implements AgentSampleFactoryTaskService {

    private final AgentSampleFactoryTaskExecutionDetailService executionDetailService;
    private final AgentSampleFactoryTaskDetailLogsService detailLogsService;
    private final AgentSampleMutationTacticsMapper mutationTacticsMapper;

    private final Map<Long, AgentSampleMutationTacticsDO> mutationTacticsMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init(){
        for (AgentSampleMutationTacticsDO agentSampleMutationTacticsDO : mutationTacticsMapper.selectList(null)) {
            mutationTacticsMap.put(agentSampleMutationTacticsDO.getMutationTacticsId(), agentSampleMutationTacticsDO);
        }
    }

    @Override
    public boolean createSampleFactoryTask(SeedCreateTask request) {
        if (request == null) {
            throw new ServiceException("样本扩充任务请求不能为空");
        }

        AgentSampleFactoryTaskDO task = new AgentSampleFactoryTaskDO();
        task.setTaskId(IdUtil.fastSimpleUUID());
        task.setTaskName(request.taskName());
        task.setBaseTargetConf(JSON.toJSONString(request.baseTargetConf()));
        task.setMutationTacticsConf(JSON.toJSONString(request.mutationTacticsConf()));
        task.setEngineParamsConf(JSON.toJSONString(request.engineParamsConf()));
        task.setStatus(1);
        task.setDeleted(false);

        LocalDateTime now = LocalDateTime.now();
        task.setCreateTime(now);
        task.setUpdateTime(now);
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId != null) {
            String operator = loginId.toString();
            task.setCreator(operator);
            task.setUpdater(operator);
        }

        return save(task);
    }

    @Override
    public List<SampleFactoryTaskVO> listSampleFactoryTasks() {
        return list(Wrappers.<AgentSampleFactoryTaskDO>lambdaQuery()
                .orderByDesc(AgentSampleFactoryTaskDO::getCreateTime)
                .orderByDesc(AgentSampleFactoryTaskDO::getId))
                .stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    public Page<SampleFactoryTaskVO> pageSampleFactoryTasks(SampleFactoryTaskPageRequest request) {
        SampleFactoryTaskPageRequest queryRequest = request == null ? new SampleFactoryTaskPageRequest() : request;
        Integer current = queryRequest.getCurrent();
        Integer size = queryRequest.getSize();
        long pageCurrent = current != null && current > 0 ? current : 1L;
        long pageSize = size != null && size > 0 ? size : 10L;
        Page<AgentSampleFactoryTaskDO> pageResult = page(new Page<>(pageCurrent, pageSize),
                Wrappers.<AgentSampleFactoryTaskDO>lambdaQuery()
                        .orderByDesc(AgentSampleFactoryTaskDO::getCreateTime)
                        .orderByDesc(AgentSampleFactoryTaskDO::getId));
        Page<SampleFactoryTaskVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream()
                .map(this::convertToVO)
                .toList());
        return voPage;
    }

    @Override
    public Boolean start(String taskId) {
        AgentSampleFactoryTaskDO task = getById(taskId);
        if (task == null) {
            throw new ServiceException("样本扩充任务不存在");
        }
        // 解析基础目标配置
        SeedCreateTask.BaseTargetConf baseTargetConf = JSON.parseObject(task.getBaseTargetConf(), SeedCreateTask.BaseTargetConf.class);
        // 解析变异战术矩阵配置
        List<SeedCreateTask.MutationTacticsConf> mutationTacticsConf = JSON.parseArray(task.getMutationTacticsConf(), SeedCreateTask.MutationTacticsConf.class);
        // 解析单批迭代引擎参数配置
        SeedCreateTask.EngineParamsConf engineParamsConf = JSON.parseObject(task.getEngineParamsConf(), SeedCreateTask.EngineParamsConf.class);
        SampleTaskTicket.TaskTicket ticket = new SampleTaskTicket.TaskTicket(
                baseTargetConf.coreIntent(),
                baseTargetConf.seed(),
                mutationTacticsConf.stream().map(mutation-> new SampleTaskTicket.TacticsContent(
                        mutationTacticsMap.get(mutation.mutationTacticsId()).getMutationTacticsName(),
                        mutationTacticsMap.get(mutation.mutationTacticsId()).getExplain(),
                        mutationTacticsMap.get(mutation.mutationTacticsId()).getExample()
                        )).toList()
                );

        // 每次启动创建独立执行批次，日志关联业务 task_id 和当前批次主键。
        AgentSampleFactoryTaskExecutionDetailDO detail = new AgentSampleFactoryTaskExecutionDetailDO();
        detail.setTaskId(task.getTaskId());
        detail.setIdempotencyKey(IdUtil.fastSimpleUUID());
        detail.setStatus("RUNNING");
        detail.setAttemptCount(0);
        detail.setDeleted(false);
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId != null) {
            detail.setCreator(loginId.toString());
            detail.setUpdater(loginId.toString());
        }
        if (!executionDetailService.save(detail) || detail.getId() == null) {
            throw new ServiceException("创建样本扩充任务执行批次失败");
        }

        boolean passed;
        try {
            passed = new SimpleOrchestrator().runReflectionLoop(ticket,
                    engineParamsConf.maxReflectionCount(), engineParamsConf.temperature(),
                    createExecutionListener(detail));
        } catch (RuntimeException exception) {
            log.error("样本扩充任务执行失败，taskId={}, taskDetailId={}", task.getTaskId(), detail.getId(), exception);
            detail.setStatus("FAILED");
            detail.setErrorMsg(exception.getClass().getSimpleName() + ": " + exception.getMessage());
            try {
                updateExecutionDetail(detail);
            } catch (RuntimeException persistenceException) {
                log.error("更新样本扩充任务失败状态异常，taskId={}, taskDetailId={}",
                        task.getTaskId(), detail.getId(), persistenceException);
                exception.addSuppressed(persistenceException);
            }
            throw exception;
        }
        detail.setStatus(passed ? "SUCCESS" : "FAILED");
        detail.setErrorMsg(passed ? null : "达到最大迭代轮数，未生成通过评审的样本");
        updateExecutionDetail(detail);
        return true;
    }

    private AgentExecutionListener createExecutionListener(AgentSampleFactoryTaskExecutionDetailDO detail) {
        return new AgentExecutionListener() {
            @Override
            public void onIterationStarted(int iteration) {
                detail.setAttemptCount(iteration);
                updateExecutionDetail(detail);
            }

            @Override
            public void onStarted(String messageId, String type, String promptContent) {
                // onIterationStarted 已在本轮调用前更新轮次，生成和评审日志共用该轮次。
                detailLogsService.startExecution(detail.getTaskId(), detail.getId(), detail.getAttemptCount(),
                        messageId, type, promptContent);
            }

            @Override
            public void onCompleted(String messageId, String contentResponse, Integer tokenCount,
                                    int latency, boolean success) {
                detailLogsService.completeExecution(messageId, contentResponse, tokenCount, latency, success);
            }
        };
    }

    private void updateExecutionDetail(AgentSampleFactoryTaskExecutionDetailDO detail) {
        // 先落执行状态，避免错误详情写入失败使批次一直停留在 RUNNING。
        AgentSampleFactoryTaskExecutionDetailDO stateUpdate = new AgentSampleFactoryTaskExecutionDetailDO();
        stateUpdate.setId(detail.getId());
        stateUpdate.setStatus(detail.getStatus());
        stateUpdate.setAttemptCount(detail.getAttemptCount());
        stateUpdate.setUpdateTime(LocalDateTime.now());
        if (!executionDetailService.updateById(stateUpdate)) {
            throw new ServiceException("更新样本扩充任务执行批次失败");
        }
        if (detail.getErrorMsg() != null) {
            AgentSampleFactoryTaskExecutionDetailDO errorUpdate = new AgentSampleFactoryTaskExecutionDetailDO();
            errorUpdate.setId(detail.getId());
            errorUpdate.setErrorMsg(detail.getErrorMsg());
            errorUpdate.setUpdateTime(LocalDateTime.now());
            if (!executionDetailService.updateById(errorUpdate)) {
                throw new ServiceException("更新样本扩充任务错误详情失败");
            }
        }
    }

    private SampleFactoryTaskVO convertToVO(AgentSampleFactoryTaskDO task) {
        SampleFactoryTaskVO vo = new SampleFactoryTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }
}
