package com.kant.llm.eval.sample.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.agent.simple.SimpleOrchestrator;
import com.kant.agent.simple.model.SampleTaskTicket;
import com.kant.framework.exception.ServiceException;
import com.kant.llm.eval.sample.dao.entity.AgentSampleFactoryTaskDO;
import com.kant.llm.eval.sample.dao.mapper.AgentSampleFactoryTaskMapper;
import com.kant.llm.eval.sample.dto.SampleFactoryTaskPageRequest;
import com.kant.llm.eval.sample.dto.SampleFactoryTaskVO;
import com.kant.llm.eval.sample.dto.SeedCreateTask;
import com.kant.llm.eval.sample.service.AgentSampleFactoryTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AgentSampleFactoryTaskServiceImpl extends ServiceImpl<AgentSampleFactoryTaskMapper, AgentSampleFactoryTaskDO> implements AgentSampleFactoryTaskService {

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
        // 提交到异步虚拟线程
        // 解析基础目标配置
        SeedCreateTask.BaseTargetConf baseTargetConf = JSON.parseObject(task.getBaseTargetConf(), SeedCreateTask.BaseTargetConf.class);
        // 解析变异战术矩阵配置
        List<SeedCreateTask.MutationTacticsConf> mutationTacticsConf = JSON.parseArray(task.getMutationTacticsConf(), SeedCreateTask.MutationTacticsConf.class);
        // 解析单批迭代引擎参数配置
        SeedCreateTask.EngineParamsConf engineParamsConf = JSON.parseObject(task.getEngineParamsConf(), SeedCreateTask.EngineParamsConf.class);
        SimpleOrchestrator orchestrator = new SimpleOrchestrator();
        orchestrator.runReflectionLoop(
                new SampleTaskTicket.TaskTicket(
                        baseTargetConf.coreIntent(),
                        baseTargetConf.seed(),
                        mutationTacticsConf.stream().map(SeedCreateTask.MutationTacticsConf::mutationTacticsName).toList()),
                engineParamsConf.maxReflectionCount(),
                engineParamsConf.temperature());
        return true;
    }

    private SampleFactoryTaskVO convertToVO(AgentSampleFactoryTaskDO task) {
        SampleFactoryTaskVO vo = new SampleFactoryTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }
}
