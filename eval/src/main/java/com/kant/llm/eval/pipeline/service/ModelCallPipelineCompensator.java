package com.kant.llm.eval.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.kant.framework.mq.producer.MessageQueueProducer;
import com.kant.framework.mq.producer.MessageSendRequest;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.common.enums.TaskStatusEnums;
import com.kant.llm.eval.pipeline.mq.message.SafetyEvaluateMessage;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.task.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.task.dao.mapper.EvalResultDetailMapper;
import com.kant.llm.eval.task.dao.mapper.EvalTaskDetailMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 模型调用异步拆分后的中间态补偿任务。
 *
 * <p>模型调用与安全评测拆成两个 MQ 阶段后，系统可能在“模型输出已落库但安全评测消息未成功投递”
 * 或“模型调用进程中断导致样本长期停留 MODEL_RUNNING”时留下中间态。该任务定时扫描这些状态，
 * 对 MODEL_CALLED 补投 SafetyEvaluate_MQ，对超时 MODEL_RUNNING 标记失败并推进批次进度。</p>
 */
@Slf4j
@Component
public class ModelCallPipelineCompensator {

    private static final String COMPENSATE_LOCK_KEY = "aigc-eval:model-call:pipeline-compensator";
    private static final int BATCH_SIZE = 100;
    private static final long MODEL_CALLED_WAIT_MINUTES = 2L;
    private static final long MODEL_RUNNING_TIMEOUT_MINUTES = 30L;
    private static final List<Integer> FINISHED_RESULT_STATUS = List.of(
            EvalResultStatusEnums.AUTO_SCORED.getCode(),
            EvalResultStatusEnums.MANUAL_REVIEWED.getCode(),
            EvalResultStatusEnums.FAILED.getCode(),
            EvalResultStatusEnums.STOPPED.getCode()
    );

    private final EvalResultDetailMapper evalResultDetailMapper;
    private final EvalTaskDetailMapper evalTaskDetailMapper;
    private final MessageQueueProducer messageQueueProducer;
    private final TransactionTemplate transactionTemplate;
    private final RedissonClient redissonClient;

    public ModelCallPipelineCompensator(EvalResultDetailMapper evalResultDetailMapper,
                                        EvalTaskDetailMapper evalTaskDetailMapper,
                                        MessageQueueProducer messageQueueProducer,
                                        TransactionTemplate transactionTemplate,
                                        RedissonClient redissonClient) {
        this.evalResultDetailMapper = evalResultDetailMapper;
        this.evalTaskDetailMapper = evalTaskDetailMapper;
        this.messageQueueProducer = messageQueueProducer;
        this.transactionTemplate = transactionTemplate;
        this.redissonClient = redissonClient;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 120_000L)
    public void compensate() {
        RLock lock = redissonClient.getLock(COMPENSATE_LOCK_KEY);
        boolean locked;
        try {
            locked = lock.tryLock(0, 55, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("模型调用流水线补偿任务获取分布式锁被中断", ex);
            return;
        }
        if (!locked) {
            return;
        }
        try {
            compensateModelCalled();
            compensateModelRunningTimeout();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void compensateModelCalled() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(MODEL_CALLED_WAIT_MINUTES);
        // MODEL_CALLED 表示模型输出已落库；停留过久通常意味着 SafetyEvaluate_MQ 投递或消费中断，需要补投。
        List<EvalResultDetailDO> records = evalResultDetailMapper.selectList(new LambdaQueryWrapper<EvalResultDetailDO>()
                .eq(EvalResultDetailDO::getStatus, EvalResultStatusEnums.MODEL_CALLED.getCode())
                .lt(EvalResultDetailDO::getUpdateTime, cutoff)
                .orderByAsc(EvalResultDetailDO::getUpdateTime)
                .last("LIMIT " + BATCH_SIZE));
        for (EvalResultDetailDO record : records) {
            EvalTaskDetailDO taskDetail = evalTaskDetailMapper.selectById(record.getTaskDetailId());
            if (taskDetail == null) {
                log.warn("模型调用完成态补偿跳过，任务批次不存在，resultDetailId: {}, taskDetailId: {}",
                        record.getId(), record.getTaskDetailId());
                continue;
            }
            if (TaskStatusEnums.STOPPED.getCode().equals(taskDetail.getStatus())) {
                markResultStopped(record.getId(), "评测任务已停止，模型调用完成样本不再进入安全评测");
                continue;
            }
            sendSafetyEvaluateMessage(record, taskDetail);
        }
        if (!records.isEmpty()) {
            log.info("模型调用完成态补偿扫描完成，补投安全评测消息数量: {}", records.size());
        }
    }

    private void compensateModelRunningTimeout() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(MODEL_RUNNING_TIMEOUT_MINUTES);
        // MODEL_RUNNING 超时通常表示模型调用进程异常退出或回写失败；这里按失败处理，避免批次永久卡住。
        List<EvalResultDetailDO> records = evalResultDetailMapper.selectList(new LambdaQueryWrapper<EvalResultDetailDO>()
                .eq(EvalResultDetailDO::getStatus, EvalResultStatusEnums.MODEL_RUNNING.getCode())
                .lt(EvalResultDetailDO::getUpdateTime, cutoff)
                .orderByAsc(EvalResultDetailDO::getUpdateTime)
                .last("LIMIT " + BATCH_SIZE));
        for (EvalResultDetailDO record : records) {
            EvalTaskDetailDO taskDetail = evalTaskDetailMapper.selectById(record.getTaskDetailId());
            if (taskDetail == null) {
                log.warn("模型调用运行态超时补偿跳过，任务批次不存在，resultDetailId: {}, taskDetailId: {}",
                        record.getId(), record.getTaskDetailId());
                continue;
            }
            if (TaskStatusEnums.STOPPED.getCode().equals(taskDetail.getStatus())) {
                markResultStopped(record.getId(), "评测任务已停止，模型调用超时样本不再继续处理");
                continue;
            }
            markResultFailedAndIncreaseProgress(record.getId(), taskDetail.getId(),
                    "模型调用阶段超时，已由补偿任务标记失败");
        }
        if (!records.isEmpty()) {
            log.info("模型调用运行态超时补偿扫描完成，处理数量: {}", records.size());
        }
    }

    private void sendSafetyEvaluateMessage(EvalResultDetailDO resultDetail, EvalTaskDetailDO taskDetail) {
        messageQueueProducer.send(MessageSendRequest.builder()
                .topic(EvalMqTopics.SAFETY_EVALUATE_TOPIC)
                .keys(resultDetail.getId().toString())
                .bizDesc("补偿单样本安全评测消息")
                .body(SafetyEvaluateMessage.builder()
                        .taskDetailId(taskDetail.getId())
                        .resultDetailId(resultDetail.getId())
                        .taskId(taskDetail.getTaskId())
                        .sampleId(resultDetail.getSampleId())
                        .modelId(taskDetail.getModelId())
                        .build())
                .build());
    }

    private void markResultStopped(Long resultDetailId, String errorMsg) {
        evalResultDetailMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                .eq(EvalResultDetailDO::getId, resultDetailId)
                .notIn(EvalResultDetailDO::getStatus, FINISHED_RESULT_STATUS)
                .set(EvalResultDetailDO::getStatus, EvalResultStatusEnums.STOPPED.getCode())
                .set(EvalResultDetailDO::getErrorMsg, trimErrorMessage(errorMsg)));
    }

    private void markResultFailedAndIncreaseProgress(Long resultDetailId, Long taskDetailId, String errorMsg) {
        transactionTemplate.executeWithoutResult(status -> {
            int updatedRows = evalResultDetailMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                    .eq(EvalResultDetailDO::getId, resultDetailId)
                    .eq(EvalResultDetailDO::getStatus, EvalResultStatusEnums.MODEL_RUNNING.getCode())
                    .set(EvalResultDetailDO::getIsSafe, false)
                    .set(EvalResultDetailDO::getErrorMsg, trimErrorMessage(errorMsg))
                    .set(EvalResultDetailDO::getStatus, EvalResultStatusEnums.FAILED.getCode()));
            if (updatedRows > 0) {
                increaseTaskProgress(taskDetailId, true);
            }
        });
    }

    private void increaseTaskProgress(Long taskDetailId, boolean failed) {
        UpdateWrapper<EvalTaskDetailDO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", taskDetailId)
                .setSql("finished_count = COALESCE(finished_count, 0) + 1");
        if (failed) {
            updateWrapper.setSql("failed_count = COALESCE(failed_count, 0) + 1");
        }
        evalTaskDetailMapper.update(null, updateWrapper);
        refreshTaskFinalStatus(taskDetailId);
    }

    private void refreshTaskFinalStatus(Long taskDetailId) {
        EvalTaskDetailDO taskDetail = evalTaskDetailMapper.selectById(taskDetailId);
        if (taskDetail == null || taskDetail.getTotalCount() == null || taskDetail.getFinishedCount() == null) {
            return;
        }
        if (taskDetail.getFinishedCount() < taskDetail.getTotalCount()) {
            return;
        }
        Integer failedCount = taskDetail.getFailedCount() == null ? 0 : taskDetail.getFailedCount();
        Integer finalStatus = Objects.equals(failedCount, 0)
                ? TaskStatusEnums.COMPLETED.getCode()
                : TaskStatusEnums.ERROR.getCode();
        evalTaskDetailMapper.update(null, new LambdaUpdateWrapper<EvalTaskDetailDO>()
                .eq(EvalTaskDetailDO::getId, taskDetailId)
                .in(EvalTaskDetailDO::getStatus, TaskStatusEnums.READY.getCode(), TaskStatusEnums.RUNNING.getCode())
                .set(EvalTaskDetailDO::getStatus, finalStatus)
                .set(EvalTaskDetailDO::getEndTime, LocalDateTime.now()));
    }

    private String trimErrorMessage(String message) {
        if (message == null) {
            return "模型调用流水线补偿失败";
        }
        int maxLength = 1000;
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
