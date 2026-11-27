package com.kant.llm.eval.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kant.framework.exception.ServiceException;
import com.alibaba.fastjson2.JSON;
import com.kant.ai.infra.client.ModelResponse;
import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.common.enums.EvalSafeStatusEnums;
import com.kant.llm.common.enums.TaskStatusEnums;
import com.kant.llm.eval.es.service.EvalEsSyncOutboxService;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.task.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.task.dao.mapper.EvalResultDetailMapper;
import com.kant.llm.eval.task.dao.mapper.EvalTaskDetailMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;

/** 安全评测的短事务边界，远程模型调用不参与数据库事务。 */
@Service
public class SafetyEvaluateResultWriter {
    private final EvalResultDetailMapper resultMapper;
    private final EvalTaskDetailMapper taskMapper;
    private final EvalEsSyncOutboxService outboxService;
    private final TransactionTemplate transactionTemplate;

    public SafetyEvaluateResultWriter(EvalResultDetailMapper resultMapper,
                                      EvalTaskDetailMapper taskMapper,
                                      EvalEsSyncOutboxService outboxService,
                                      TransactionTemplate transactionTemplate) {
        this.resultMapper = resultMapper;
        this.taskMapper = taskMapper;
        this.outboxService = outboxService;
        this.transactionTemplate = transactionTemplate;
    }

    /** 在结果分布式锁内调用；异常重试可以接管上次未完成的 SAFETY_EVALUATING。 */
    public EvalResultDetailDO claim(Long resultDetailId, Integer executionVersion) {
        return transactionTemplate.execute(transaction -> {
            EvalResultDetailDO result = resultMapper.selectById(resultDetailId);
            if (!ResultExecution.current(result, executionVersion) || ResultExecution.terminal(result.getStatus())) {
                return null;
            }
            if (!isEvaluable(result.getStatus())) {
                throw new ServiceException("模型输出尚未就绪，resultDetailId: " + resultDetailId);
            }
            EvalTaskDetailDO task = lockTask(result.getTaskDetailId());
            if (isStopped(task)) {
                markStopped(result);
                return null;
            }
            if (!isActive(task)) {
                return null;
            }
            if (result.getModelOutput() == null || result.getModelOutput().isBlank()
                    || result.getRiskDetailsId() == null) {
                throw new ServiceException("模型输出或风险分类缺失，resultDetailId: " + resultDetailId);
            }
            if (Objects.equals(result.getStatus(), EvalResultStatusEnums.SAFETY_EVALUATING.getCode())) {
                // 已持有分布式锁，可以接管异常中断的执行；不依赖无变化 UPDATE 的影响行数。
                result.setIsSafe(EvalSafeStatusEnums.UNJUDGED.getCode());
                result.setErrorMsg(null);
                return result;
            }
            int updated = resultMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                    .eq(EvalResultDetailDO::getId, resultDetailId)
                    .eq(EvalResultDetailDO::getExecutionVersion, ResultExecution.version(executionVersion))
                    .eq(EvalResultDetailDO::getStatus, EvalResultStatusEnums.MODEL_CALLED.getCode())
                    .set(EvalResultDetailDO::getStatus, EvalResultStatusEnums.SAFETY_EVALUATING.getCode())
                    .set(EvalResultDetailDO::getIsSafe, EvalSafeStatusEnums.UNJUDGED.getCode())
                    .set(EvalResultDetailDO::getErrorMsg, null));
            if (updated != 1) {
                return null;
            }
            outboxService.recordResultDetailUpsert(resultDetailId);
            result.setStatus(EvalResultStatusEnums.SAFETY_EVALUATING.getCode());
            result.setIsSafe(EvalSafeStatusEnums.UNJUDGED.getCode());
            result.setErrorMsg(null);
            return result;
        });
    }

    public boolean isTaskStopped(Long taskDetailId) {
        EvalTaskDetailDO task = taskMapper.selectById(taskDetailId);
        return task == null || isStopped(task);
    }

    /** 结果终态、自动评测进度及 ES 同步事件一起提交；待人工核验也表示自动评测阶段结束。 */
    public void complete(EvalResultDetailDO result) {
        transactionTemplate.executeWithoutResult(transaction -> {
            // 所有样本先锁批次行，与停止操作保持相同的加锁顺序。
            EvalTaskDetailDO task = lockTask(result.getTaskDetailId());
            if (isStopped(task)) {
                markStopped(result);
                return;
            }
            if (!isActive(task)) {
                return;
            }
            boolean finished = Objects.equals(result.getStatus(), EvalResultStatusEnums.FINISHED.getCode());
            boolean manualReview = Objects.equals(result.getStatus(), EvalResultStatusEnums.MANUAL_REVIEWED.getCode());
            if ((!finished && !manualReview)
                    || (manualReview && !Objects.equals(result.getIsSafe(), EvalSafeStatusEnums.UNJUDGED.getCode()))
                    || (finished
                    && !Objects.equals(result.getIsSafe(), EvalSafeStatusEnums.SAFE.getCode())
                    && !Objects.equals(result.getIsSafe(), EvalSafeStatusEnums.VIOLATION.getCode()))) {
                throw new ServiceException("安全评测尚未形成有效结论，resultDetailId: " + result.getId());
            }
            int updated = resultMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                    .eq(EvalResultDetailDO::getId, result.getId())
                    .eq(EvalResultDetailDO::getTaskDetailId, result.getTaskDetailId())
                    .eq(EvalResultDetailDO::getExecutionVersion, ResultExecution.version(result.getExecutionVersion()))
                    .eq(EvalResultDetailDO::getStatus, EvalResultStatusEnums.SAFETY_EVALUATING.getCode())
                    .eq(EvalResultDetailDO::getProgressCounted, false)
                    .set(EvalResultDetailDO::getStatus, result.getStatus())
                    .set(EvalResultDetailDO::getIsSafe, result.getIsSafe())
                    .set(EvalResultDetailDO::getErrorMsg, result.getErrorMsg())
                    .set(EvalResultDetailDO::getProgressCounted, true));
            if (updated == 0) {
                return;
            }
            outboxService.recordResultDetailUpsert(result.getId());
            LambdaUpdateWrapper<EvalTaskDetailDO> progress = new LambdaUpdateWrapper<EvalTaskDetailDO>()
                    .eq(EvalTaskDetailDO::getId, task.getId())
                    .in(EvalTaskDetailDO::getStatus, TaskStatusEnums.READY.getCode(), TaskStatusEnums.RUNNING.getCode())
                    .setSql("finished_count = COALESCE(finished_count, 0) + 1");
            int completed = (task.getFinishedCount() == null ? 0 : task.getFinishedCount()) + 1;
            if (task.getTotalCount() != null && completed >= task.getTotalCount()) {
                int failed = task.getFailedCount() == null ? 0 : task.getFailedCount();
                progress.set(EvalTaskDetailDO::getStatus, failed == 0
                                ? TaskStatusEnums.COMPLETED.getCode() : TaskStatusEnums.ERROR.getCode())
                        .set(EvalTaskDetailDO::getEndTime, LocalDateTime.now());
            }
            if (taskMapper.update(null, progress) != 1) {
                throw new ServiceException("安全评测批次进度更新失败，taskDetailId: " + task.getId());
            }
        });
    }

    private EvalTaskDetailDO lockTask(Long taskDetailId) {
        EvalTaskDetailDO task = taskMapper.selectOne(new LambdaQueryWrapper<EvalTaskDetailDO>()
                .eq(EvalTaskDetailDO::getId, taskDetailId).last("FOR UPDATE"));
        if (task == null) {
            throw new ServiceException("评测任务批次不存在，taskDetailId: " + taskDetailId);
        }
        return task;
    }

    private void markStopped(EvalResultDetailDO result) {
        int updated = resultMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                .eq(EvalResultDetailDO::getId, result.getId())
                .eq(EvalResultDetailDO::getExecutionVersion, ResultExecution.version(result.getExecutionVersion()))
                .in(EvalResultDetailDO::getStatus, EvalResultStatusEnums.PENDING.getCode(),
                        EvalResultStatusEnums.MODEL_RUNNING.getCode(), EvalResultStatusEnums.MODEL_CALLED.getCode(),
                        EvalResultStatusEnums.SAFETY_EVALUATING.getCode())
                .set(EvalResultDetailDO::getStatus, EvalResultStatusEnums.STOPPED.getCode())
                .set(EvalResultDetailDO::getIsSafe, EvalSafeStatusEnums.UNJUDGED.getCode())
                .set(EvalResultDetailDO::getErrorMsg, "用户主动终止评测，停止安全评测"));
        if (updated > 0) {
            outboxService.recordResultDetailUpsert(result.getId());
        }
    }

    public EvalResultDetailDO claimModel(Long resultDetailId, Integer executionVersion) {
        return transactionTemplate.execute(transaction -> {
            EvalResultDetailDO result = resultMapper.selectById(resultDetailId);
            if (!ResultExecution.current(result, executionVersion) || ResultExecution.terminal(result.getStatus())) {
                return null;
            }
            EvalTaskDetailDO task = lockTask(result.getTaskDetailId());
            if (isStopped(task)) {
                markStopped(result);
                return null;
            }
            if (!isActive(task)) {
                return null;
            }
            if (Objects.equals(result.getStatus(), EvalResultStatusEnums.MODEL_CALLED.getCode())) {
                return result; // 输出已落库时，只补投安全评测，不能再次调用模型。
            }
            if (Objects.equals(result.getStatus(), EvalResultStatusEnums.MODEL_RUNNING.getCode())) {
                return result; // 持有执行锁，可以接管异常中断的模型调用。
            }
            if (!Objects.equals(result.getStatus(), EvalResultStatusEnums.PENDING.getCode())) {
                return null;
            }
            int updated = resultMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                    .eq(EvalResultDetailDO::getId, resultDetailId)
                    .eq(EvalResultDetailDO::getExecutionVersion, ResultExecution.version(executionVersion))
                    .eq(EvalResultDetailDO::getStatus, EvalResultStatusEnums.PENDING.getCode())
                    .set(EvalResultDetailDO::getStatus, EvalResultStatusEnums.MODEL_RUNNING.getCode())
                    .set(EvalResultDetailDO::getErrorMsg, null));
            if (updated != 1) {
                return null;
            }
            outboxService.recordResultDetailUpsert(resultDetailId);
            result.setStatus(EvalResultStatusEnums.MODEL_RUNNING.getCode());
            return result;
        });
    }

    public boolean completeModel(EvalResultDetailDO result, ModelResponse response) {
        return Boolean.TRUE.equals(transactionTemplate.execute(transaction -> {
            EvalTaskDetailDO task = lockTask(result.getTaskDetailId());
            if (isStopped(task)) {
                markStopped(result);
                return false;
            }
            if (!isActive(task)) {
                return false;
            }
            int updated = resultMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                    .eq(EvalResultDetailDO::getId, result.getId())
                    .eq(EvalResultDetailDO::getExecutionVersion, ResultExecution.version(result.getExecutionVersion()))
                    .eq(EvalResultDetailDO::getStatus, EvalResultStatusEnums.MODEL_RUNNING.getCode())
                    .set(EvalResultDetailDO::getModelOutput, response.getRespContent())
                    .set(EvalResultDetailDO::getRawResponse, JSON.toJSONString(response))
                    .set(EvalResultDetailDO::getLatency, Math.toIntExact(response.getElapsed()))
                    .set(EvalResultDetailDO::getErrorMsg, null)
                    .set(EvalResultDetailDO::getStatus, EvalResultStatusEnums.MODEL_CALLED.getCode()));
            if (updated == 1) {
                outboxService.recordResultDetailUpsert(result.getId());
            }
            return updated == 1;
        }));
    }

    /** 异常交给 MQ 重试，保留阶段和轮次，同时记录原因供人工重试判断。 */
    public void recordFailure(Long resultId, Integer executionVersion, RuntimeException failure) {
        transactionTemplate.executeWithoutResult(transaction -> {
            EvalResultDetailDO result = resultMapper.selectById(resultId);
            if (!ResultExecution.current(result, executionVersion) || ResultExecution.terminal(result.getStatus())) {
                return;
            }
            EvalTaskDetailDO task = lockTask(result.getTaskDetailId());
            if (isStopped(task)) {
                markStopped(result);
                return;
            }
            if (!isActive(task)) {
                return;
            }
            String error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            int updated = resultMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                    .eq(EvalResultDetailDO::getId, resultId)
                    .eq(EvalResultDetailDO::getExecutionVersion, ResultExecution.version(executionVersion))
                    .in(EvalResultDetailDO::getStatus, EvalResultStatusEnums.PENDING.getCode(),
                            EvalResultStatusEnums.MODEL_RUNNING.getCode(), EvalResultStatusEnums.MODEL_CALLED.getCode(),
                            EvalResultStatusEnums.SAFETY_EVALUATING.getCode())
                    .set(EvalResultDetailDO::getErrorMsg, error.substring(0, Math.min(error.length(), 1000))));
            if (updated > 0) {
                outboxService.recordResultDetailUpsert(resultId);
            }
        });
    }

    private boolean isEvaluable(Integer status) {
        return Objects.equals(status, EvalResultStatusEnums.MODEL_CALLED.getCode())
                || Objects.equals(status, EvalResultStatusEnums.SAFETY_EVALUATING.getCode());
    }

    private boolean isStopped(EvalTaskDetailDO task) {
        return Objects.equals(task.getStatus(), TaskStatusEnums.STOPPED.getCode());
    }

    private boolean isActive(EvalTaskDetailDO task) {
        return Objects.equals(task.getStatus(), TaskStatusEnums.READY.getCode())
                || Objects.equals(task.getStatus(), TaskStatusEnums.RUNNING.getCode());
    }
}
