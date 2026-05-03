package com.kant.llm.eval.scheduler;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.llm.eval.common.enums.TaskStatusEnums;
import com.kant.llm.eval.dao.entity.DataSetSampleDO;
import com.kant.llm.eval.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.dao.mapper.DataSetSampleMapper;
import com.kant.llm.eval.dao.mapper.EvalResultDetailMapper;
import com.kant.llm.eval.dao.mapper.EvalTaskDetailMapper;
import com.kant.llm.eval.service.EvalTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 评测定时任务
 */
@Slf4j
@Component
public class EvalTaskScheduler {

    private final EvalTaskService evalTaskService;
    private final EvalTaskDetailMapper evalTaskDetailMapper;
    private final EvalResultDetailMapper evalResultDetailMapper;
    private final DataSetSampleMapper dataSetSampleMapper;


    public EvalTaskScheduler(EvalTaskService evalTaskService,
                             EvalTaskDetailMapper evalTaskDetailMapper,
                             EvalResultDetailMapper evalResultDetailMapper,
                             DataSetSampleMapper dataSetSampleMapper) {
        this.evalTaskService = evalTaskService;
        this.evalTaskDetailMapper = evalTaskDetailMapper;
        this.evalResultDetailMapper = evalResultDetailMapper;
        this.dataSetSampleMapper = dataSetSampleMapper;
    }

    /**
     * 定时任务 每秒执行一次任务
     */
    @Scheduled(fixedDelay = 1000)
    public void schedule() {
        List<EvalTaskDetailDO> evalTaskDetailDOS =
                evalTaskService.selectPendingTasks();

        if (CollectionUtil.isEmpty(evalTaskDetailDOS)) {
            return;
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            evalTaskDetailDOS.forEach(taskDetail -> {
                taskDetail.setStatus(TaskStatusEnums.INITIALIZING.getCode());
                taskDetail.setStartTime(LocalDateTime.now());
                evalTaskDetailMapper.updateById(taskDetail);
                log.info("提交到异步任务，任务ID:{},子任务ID：{}", taskDetail.getTaskId(), taskDetail.getId());
                executor.submit(() -> executeTask(taskDetail));
            });
        }

        log.info("schedule...........................");
    }

    private void executeTask(EvalTaskDetailDO taskDetail) {
        List<DataSetSampleDO> dataSetSampleDOS = dataSetSampleMapper.selectList(new LambdaQueryWrapper<>(DataSetSampleDO.class)
                .eq(DataSetSampleDO::getDatasetId, taskDetail.getDatasetId()));

        dataSetSampleDOS.forEach(dataSetSample -> evalResultDetailMapper.insert(
                EvalResultDetailDO.builder()
                        .taskId(taskDetail.getTaskId())
                        .sampleId(dataSetSample.getId())
                        .inputText(dataSetSample.getInputText())
                        .status(0)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build()));
    }

}
