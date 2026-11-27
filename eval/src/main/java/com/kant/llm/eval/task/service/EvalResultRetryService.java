package com.kant.llm.eval.task.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kant.framework.errorcode.BaseErrorCode;
import com.kant.framework.exception.ServiceException;
import com.kant.framework.mq.producer.MessageQueueProducer;
import com.kant.framework.mq.producer.MessageSendRequest;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.common.enums.EvalSafeStatusEnums;
import com.kant.llm.common.enums.TaskStatusEnums;
import com.kant.llm.eval.es.service.EvalEsSyncOutboxService;
import com.kant.llm.eval.pipeline.service.ResultExecution;
import com.kant.llm.eval.pipeline.record.ModelCallMessage;
import com.kant.llm.eval.pipeline.record.SafetyEvaluateMessage;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.task.dao.entity.EvalResultRetryDO;
import com.kant.llm.eval.task.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.task.dao.mapper.EvalResultDetailMapper;
import com.kant.llm.eval.task.dao.mapper.EvalResultRetryMapper;
import com.kant.llm.eval.task.dao.mapper.EvalTaskDetailMapper;
import com.kant.llm.eval.task.dto.req.RetryEvalResultRequest;
import com.kant.llm.eval.task.dto.resp.RetryEvalResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class EvalResultRetryService {
    private final EvalResultDetailMapper resultMapper;
    private final EvalTaskDetailMapper taskMapper;
    private final EvalResultRetryMapper retryMapper;
    private final EvalEsSyncOutboxService outboxService;
    private final RedissonClient redisson;
    private final MessageQueueProducer producer;
    private final TransactionTemplate transactions;

    public RetryEvalResultVO retry(RetryEvalResultRequest request) {
        validate(request);
        EvalResultRetryDO existing = findRequest(request.getRequestId());
        if (existing != null) {
            return replay(existing, request);
        }
        EvalResultDetailDO result = requireResult(request.getResultDetailId());
        RLock taskLock = redisson.getLock("aigc-eval:eval-task:operation:" + result.getTaskId());
        RLock executionLock = redisson.getLock(ResultExecution.lockKey(result.getId()));
        boolean taskLocked = false;
        boolean executionLocked = false;
        try {
            taskLocked = taskLock.tryLock(0, TimeUnit.SECONDS);
            if (!taskLocked) {
                throw new ServiceException("评测任务正在操作中，请稍后重试");
            }
            // 与正在执行的模型/安全评测互斥；不强行接管持锁的执行。
            executionLocked = executionLock.tryLock(0, TimeUnit.SECONDS);
            if (!executionLocked) {
                throw new ServiceException("该样本正在执行中，不能重复重试");
            }
            existing = findRequest(request.getRequestId());
            if (existing != null) {
                return replay(existing, request);
            }
            RetryPlan plan = transactions.execute(tx -> prepare(request));
            if (plan.queued()) {
                return response(plan.record());
            }
            producer.sendInTransaction(MessageSendRequest.builder()
                    .topic(plan.modelCall() ? EvalMqTopics.MODEL_CALL_TOPIC : EvalMqTopics.SAFETY_EVALUATE_TOPIC)
                    .keys(request.getRequestId())
                    .bizDesc("单样本重试")
                    .body(message(plan))
                    .localTransaction(ignored -> commitRetry(plan))
                    .build());
            // 事务适配器必须确认本地提交；此处再以持久记录确认受理结果。
            EvalResultRetryDO committed = findRequest(request.getRequestId());
            if (committed == null) {
                throw new ServiceException("重试尚未提交，请使用相同requestId查询或重试");
            }
            return response(committed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("单样本重试被中断", e, BaseErrorCode.SERVICE_ERROR);
        } finally {
            release(executionLock, executionLocked);
            release(taskLock, taskLocked);
        }
    }

    private RetryPlan prepare(RetryEvalResultRequest request) {
        EvalResultDetailDO result = requireResult(request.getResultDetailId());
        EvalTaskDetailDO task = lockTask(result.getTaskDetailId());
        validateBatch(task);
        if (Objects.equals(result.getStatus(), EvalResultStatusEnums.STOPPED.getCode())) {
            throw new ServiceException("已停止的样本不支持重试，请重新提交任务");
        }
        if (result.getStatus() == null || (result.getStatus() != EvalResultStatusEnums.PENDING.getCode()
                && result.getStatus() != EvalResultStatusEnums.MODEL_RUNNING.getCode()
                && result.getStatus() != EvalResultStatusEnums.MODEL_CALLED.getCode()
                && result.getStatus() != EvalResultStatusEnums.SAFETY_EVALUATING.getCode()
                && !ResultExecution.terminal(result.getStatus()))) {
            throw new ServiceException("当前样本状态不支持重试");
        }
        boolean queued = (Objects.equals(result.getStatus(), EvalResultStatusEnums.PENDING.getCode())
                || Objects.equals(result.getStatus(), EvalResultStatusEnums.MODEL_CALLED.getCode()))
                && (Objects.equals(task.getStatus(), TaskStatusEnums.READY.getCode())
                    || Objects.equals(task.getStatus(), TaskStatusEnums.RUNNING.getCode()))
                && (result.getErrorMsg() == null || result.getErrorMsg().isBlank());
        boolean modelCall = queued
                ? Objects.equals(result.getStatus(), EvalResultStatusEnums.PENDING.getCode())
                : request.getMode() == RetryEvalResultRequest.Mode.FULL
                    || result.getModelOutput() == null || result.getModelOutput().isBlank();
        int version = ResultExecution.version(result.getExecutionVersion());
        EvalResultRetryDO record = EvalResultRetryDO.builder()
                .id(IdUtil.getSnowflake().nextId()).requestId(request.getRequestId())
                .resultDetailId(result.getId()).taskDetailId(task.getId())
                .executionVersion(queued ? version : Math.incrementExact(version))
                .mode(request.getMode().name()).startStage(modelCall ? "MODEL_CALL" : "SAFETY_EVALUATE")
                .createTime(LocalDateTime.now()).build();
        if (queued) {
            // 持久保存请求别名，确保这次请求在样本完成后重放也不会启动新轮次。
            requireOne(retryMapper.insert(record), "重试请求记录失败");
        }
        return new RetryPlan(result, task, record, modelCall, queued);
    }

    private void commitRetry(RetryPlan plan) {
        // 由 RocketMQ 本地事务回调开启事务；所有检查和更新均在该事务内完成。
        EvalTaskDetailDO task = lockTask(plan.task().getId());
        validateBatch(task);
        EvalResultDetailDO current = requireResult(plan.result().getId());
        if (!Objects.equals(current.getStatus(), plan.result().getStatus())
                || !ResultExecution.current(current, plan.result().getExecutionVersion())) {
            throw new ServiceException("样本状态已经变化，请重新查询");
        }
        if (current.getRiskDetailsId() == null) {
            throw new ServiceException("样本缺少风险分类");
        }
        boolean counted = Boolean.TRUE.equals(current.getProgressCounted());
        boolean failed = counted && Objects.equals(current.getStatus(), EvalResultStatusEnums.FAILED.getCode());
        int finishedCount = task.getFinishedCount() == null ? 0 : task.getFinishedCount();
        int failedCount = task.getFailedCount() == null ? 0 : task.getFailedCount();
        if ((counted && finishedCount <= 0) || (failed && failedCount <= 0)) {
            throw new ServiceException("批次计数与样本状态不一致，无法重试");
        }
        LambdaUpdateWrapper<EvalResultDetailDO> reset = new LambdaUpdateWrapper<EvalResultDetailDO>()
                .eq(EvalResultDetailDO::getId, current.getId())
                .eq(EvalResultDetailDO::getStatus, current.getStatus())
                .eq(EvalResultDetailDO::getExecutionVersion, ResultExecution.version(current.getExecutionVersion()))
                .set(EvalResultDetailDO::getExecutionVersion, plan.record().getExecutionVersion())
                .set(EvalResultDetailDO::getProgressCounted, false)
                .set(EvalResultDetailDO::getIsSafe, EvalSafeStatusEnums.UNJUDGED.getCode())
                .set(EvalResultDetailDO::getErrorMsg, null)
                .set(EvalResultDetailDO::getStatus, plan.modelCall()
                        ? EvalResultStatusEnums.PENDING.getCode() : EvalResultStatusEnums.MODEL_CALLED.getCode());
        if (plan.modelCall()) {
            reset.set(EvalResultDetailDO::getModelOutput, null)
                    .set(EvalResultDetailDO::getRawResponse, null)
                    .set(EvalResultDetailDO::getLatency, null);
        }
        requireOne(resultMapper.update(null, reset), "重试状态更新失败");
        if (counted || !Objects.equals(task.getStatus(), TaskStatusEnums.RUNNING.getCode())
                || task.getEndTime() != null) {
            // 不依赖无变化 UPDATE 的影响行数，兼容 MySQL useAffectedRows=true。
            requireOne(taskMapper.update(null, new LambdaUpdateWrapper<EvalTaskDetailDO>()
                    .eq(EvalTaskDetailDO::getId, task.getId())
                    .set(EvalTaskDetailDO::getFinishedCount, finishedCount - (counted ? 1 : 0))
                    .set(EvalTaskDetailDO::getFailedCount, failedCount - (failed ? 1 : 0))
                    .set(EvalTaskDetailDO::getStatus, TaskStatusEnums.RUNNING.getCode())
                    .set(EvalTaskDetailDO::getEndTime, null)), "批次重试进度更新失败");
        }
        requireOne(retryMapper.insert(plan.record()), "重试请求记录失败");
        outboxService.recordResultDetailUpsert(current.getId());
    }

    private Object message(RetryPlan plan) {
        EvalResultDetailDO result = plan.result();
        if (plan.modelCall()) {
            return ModelCallMessage.builder().resultDetailId(result.getId()).taskId(result.getTaskId())
                    .taskDetailId(result.getTaskDetailId()).sampleId(result.getSampleId())
                    .modelId(plan.task().getModelId()).executionVersion(plan.record().getExecutionVersion())
                    .retryRequestId(plan.record().getRequestId()).build();
        }
        return SafetyEvaluateMessage.builder().resultDetailId(result.getId()).taskId(result.getTaskId())
                .taskDetailId(result.getTaskDetailId()).sampleId(result.getSampleId())
                .modelId(plan.task().getModelId()).executionVersion(plan.record().getExecutionVersion())
                .retryRequestId(plan.record().getRequestId()).build();
    }

    private void validateBatch(EvalTaskDetailDO task) {
        if (task == null) {
            throw new ServiceException("评测批次不存在");
        }
        if (Objects.equals(task.getStatus(), TaskStatusEnums.STOPPED.getCode())) {
            throw new ServiceException("已停止的批次不支持重试，请重新提交任务");
        }
        if (!Objects.equals(task.getStatus(), TaskStatusEnums.READY.getCode())
                && !Objects.equals(task.getStatus(), TaskStatusEnums.RUNNING.getCode())
                && !Objects.equals(task.getStatus(), TaskStatusEnums.COMPLETED.getCode())
                && !Objects.equals(task.getStatus(), TaskStatusEnums.ERROR.getCode())) {
            throw new ServiceException("当前批次状态不支持重试");
        }
        EvalTaskDetailDO latest = taskMapper.selectOne(new LambdaQueryWrapper<EvalTaskDetailDO>()
                .eq(EvalTaskDetailDO::getTaskId, task.getTaskId())
                .orderByDesc(EvalTaskDetailDO::getCreateTime).orderByDesc(EvalTaskDetailDO::getId).last("LIMIT 1"));
        if (latest == null || !Objects.equals(latest.getId(), task.getId())) {
            throw new ServiceException("仅支持最近一次批次的单样本重试");
        }
        Long active = taskMapper.selectCount(new LambdaQueryWrapper<EvalTaskDetailDO>()
                .eq(EvalTaskDetailDO::getTaskId, task.getTaskId())
                .ne(EvalTaskDetailDO::getId, task.getId())
                .in(EvalTaskDetailDO::getStatus, TaskStatusEnums.CREATING.getCode(),
                        TaskStatusEnums.INITIALIZING.getCode(), TaskStatusEnums.READY.getCode(),
                        TaskStatusEnums.RUNNING.getCode()));
        if (active != null && active > 0) {
            throw new ServiceException("该任务已有其他活跃批次");
        }
    }

    private EvalResultDetailDO requireResult(Long id) {
        EvalResultDetailDO result = resultMapper.selectById(id);
        if (result == null) {
            throw new ServiceException("评测结果明细不存在");
        }
        return result;
    }

    private EvalTaskDetailDO lockTask(Long id) {
        return taskMapper.selectOne(new LambdaQueryWrapper<EvalTaskDetailDO>()
                .eq(EvalTaskDetailDO::getId, id).last("FOR UPDATE"));
    }

    private EvalResultRetryDO findRequest(String requestId) {
        return retryMapper.selectOne(new LambdaQueryWrapper<EvalResultRetryDO>()
                .eq(EvalResultRetryDO::getRequestId, requestId));
    }

    private RetryEvalResultVO replay(EvalResultRetryDO record, RetryEvalResultRequest request) {
        if (!Objects.equals(record.getResultDetailId(), request.getResultDetailId())
                || !Objects.equals(record.getMode(), request.getMode().name())) {
            throw new ServiceException("requestId已被其他重试请求使用");
        }
        return response(record);
    }

    private RetryEvalResultVO response(EvalResultRetryDO record) {
        return new RetryEvalResultVO(record.getResultDetailId(), record.getExecutionVersion(),
                record.getStartStage(), record.getRequestId(), "ACCEPTED");
    }

    private void validate(RetryEvalResultRequest request) {
        if (request == null || request.getResultDetailId() == null || request.getResultDetailId() <= 0
                || request.getMode() == null || request.getRequestId() == null
                || !request.getRequestId().matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new ServiceException("单样本重试参数不合法");
        }
    }

    private void requireOne(int rows, String message) {
        if (rows != 1) {
            throw new ServiceException(message);
        }
    }

    private void release(RLock lock, boolean acquired) {
        if (acquired) {
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } catch (RuntimeException e) {
                log.warn("单样本重试释放锁失败", e);
            }
        }
    }

    private record RetryPlan(EvalResultDetailDO result, EvalTaskDetailDO task, EvalResultRetryDO record,
                             boolean modelCall, boolean queued) {
    }
}
