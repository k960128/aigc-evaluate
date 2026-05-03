package com.kant.llm.eval.scheduler;

import com.kant.llm.eval.common.enums.TaskStatusEnums;
import com.kant.llm.eval.dao.entity.EvalTaskDetailDO;
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


    public EvalTaskScheduler(EvalTaskService evalTaskService,
                             EvalTaskDetailMapper evalTaskDetailMapper) {
        this.evalTaskService = evalTaskService;
        this.evalTaskDetailMapper = evalTaskDetailMapper;
    }

    /**
     * 定时任务 每秒执行一次任务
     */
    @Scheduled(fixedDelay = 1000)
    public void schedule() {
        List<EvalTaskDetailDO> evalTaskDetailDOS =
                evalTaskService.selectPendingTasks();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            evalTaskDetailDOS.forEach(taskDetail -> {
                taskDetail.setStatus(TaskStatusEnums.INITIALIZING.getCode());
                taskDetail.setStartTime(LocalDateTime.now());
                evalTaskDetailMapper.updateById(taskDetail);
                log.info("提交到异步任务，任务ID：{}", taskDetail.getId());
                executor.submit(() -> executeTask(taskDetail));
            });
        }

        log.info("schedule...........................");
    }

    private void executeTask(EvalTaskDetailDO taskDetail) {

    }

}
