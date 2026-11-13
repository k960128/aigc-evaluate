package com.kant.llm.eval.newpipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kant.framework.exception.ServiceException;
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
import java.util.Set;

/** 安全评测的短事务边界，远程模型调用不参与数据库事务。 */
@Service
public class SafetyEvaluateResultWriter {
    private static final Set<Integer> TERMINAL_RESULT_STATUS = Set.of(
            EvalResultStatusEnums.FINISHED.getCode(),
            EvalResultStatusEnums.AUTO_SCORED.getCode(),
            EvalResultStatusEnums.MANUAL_REVIEWED.getCode(),
            EvalResultStatusEnums.FAILED.getCode(),
            EvalResultStatusEnums.STOPPED.getCode());

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
    public EvalResultDetailDO claim(Long resultDetailId) {
        return transactionTemplate.execute(transaction -> {
            EvalResultDetailDO result = resultMapper.selectById(resultDetailId);
            if (result == null || (result.getStatus() != null && TERMINAL_RESULT_STATUS.contains(result.getStatus()))) {
                return null;
            }
            if (!isEvaluable(result.getStatus())) {
                throw new ServiceException("模型输出尚未就绪，resultDetailId: " + resultDetailId);
            }
            EvalTaskDetailDO task = lockTask(result.getTaskDetailId());
            if (isStopped(task)) {
                markStopped(resultDetailId);
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
                markStopped(result.getId());
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
                    .eq(EvalResultDetailDO::getStatus, EvalResultStatusEnums.SAFETY_EVALUATING.getCode())
                    .set(EvalResultDetailDO::getStatus, result.getStatus())
                    .set(EvalResultDetailDO::getIsSafe, result.getIsSafe())
                    .set(EvalResultDetailDO::getErrorMsg, result.getErrorMsg()));
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

    private void markStopped(Long resultDetailId) {
        int updated = resultMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                .eq(EvalResultDetailDO::getId, resultDetailId)
                .in(EvalResultDetailDO::getStatus, EvalResultStatusEnums.MODEL_CALLED.getCode(),
                        EvalResultStatusEnums.SAFETY_EVALUATING.getCode())
                .set(EvalResultDetailDO::getStatus, EvalResultStatusEnums.STOPPED.getCode())
                .set(EvalResultDetailDO::getIsSafe, EvalSafeStatusEnums.UNJUDGED.getCode())
                .set(EvalResultDetailDO::getErrorMsg, "用户主动终止评测，停止安全评测"));
        if (updated > 0) {
            outboxService.recordResultDetailUpsert(resultDetailId);
        }
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
