package com.kant.llm.pipeline;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.kant.framework.exception.ServiceException;
import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.common.enums.EvalSafeStatusEnums;
import com.kant.llm.common.enums.TaskStatusEnums;
import com.kant.llm.eval.pipeline.service.SafetyEvaluateResultWriter;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.task.dao.mapper.EvalTaskDetailMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** 使用真实 SQL 和事务验证 CAS、回滚及同批次多样本并发，不启动外部服务。 */
class SafetyEvaluateResultWriterTest extends ResultExecutionTestDatabase {
    @Test
    void oneSampleDoesNotCompleteEntireBatch() {
        insertTask(100, 0, 0, TaskStatusEnums.RUNNING);
        insertResult(1L, EvalResultStatusEnums.SAFETY_EVALUATING);

        writer.complete(outcome(1L, EvalResultStatusEnums.FINISHED));

        assertThat(taskInt("finished_count")).isEqualTo(1);
        assertThat(taskInt("status")).isEqualTo(TaskStatusEnums.RUNNING.getCode());
        assertThat(jdbc.queryForObject("SELECT end_time FROM eval_task_detail WHERE id = 20", Object.class)).isNull();
        assertThat(resultMapper.selectById(1L).getStatus()).isEqualTo(EvalResultStatusEnums.FINISHED.getCode());
    }

    @Test
    void lastSampleCompletesBatch() {
        insertTask(2, 1, 0, TaskStatusEnums.RUNNING);
        insertResult(1L, EvalResultStatusEnums.SAFETY_EVALUATING);

        writer.complete(outcome(1L, EvalResultStatusEnums.FINISHED));

        assertThat(taskInt("finished_count")).isEqualTo(2);
        assertThat(taskInt("status")).isEqualTo(TaskStatusEnums.COMPLETED.getCode());
        assertThat(jdbc.queryForObject("SELECT end_time FROM eval_task_detail WHERE id = 20", Object.class)).isNotNull();
    }

    @Test
    void existingFailuresMakeCompletedBatchError() {
        insertTask(2, 1, 1, TaskStatusEnums.RUNNING);
        insertResult(1L, EvalResultStatusEnums.SAFETY_EVALUATING);

        writer.complete(outcome(1L, EvalResultStatusEnums.FINISHED));

        assertThat(taskInt("finished_count")).isEqualTo(2);
        assertThat(taskInt("failed_count")).isEqualTo(1);
        assertThat(taskInt("status")).isEqualTo(TaskStatusEnums.ERROR.getCode());
    }

    @Test
    void manualReviewPersistsWithoutInventingSafetyVerdict() {
        insertTask(2, 0, 0, TaskStatusEnums.RUNNING);
        insertResult(1L, EvalResultStatusEnums.SAFETY_EVALUATING);
        EvalResultDetailDO outcome = outcome(1L, EvalResultStatusEnums.MANUAL_REVIEWED);
        outcome.setIsSafe(EvalSafeStatusEnums.UNJUDGED.getCode());
        outcome.setErrorMsg("待人工核验");

        writer.complete(outcome);

        EvalResultDetailDO stored = resultMapper.selectById(1L);
        assertThat(stored.getStatus()).isEqualTo(EvalResultStatusEnums.MANUAL_REVIEWED.getCode());
        assertThat(stored.getIsSafe()).isEqualTo(EvalSafeStatusEnums.UNJUDGED.getCode());
        assertThat(stored.getErrorMsg()).isEqualTo("待人工核验");
        assertThat(taskInt("finished_count")).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = EvalResultStatusEnums.class,
            names = {"FINISHED", "AUTO_SCORED", "MANUAL_REVIEWED", "FAILED", "STOPPED"})
    void terminalResultIsNotClaimedAgain(EvalResultStatusEnums status) {
        insertResult(1L, status);

        assertThat(writer.claim(1L, 0)).isNull();

        verifyNoInteractions(outbox);
    }

    @Test
    void interruptedAttemptCanBeRetriedWithoutIncreasingProgress() {
        insertTask(2, 0, 0, TaskStatusEnums.RUNNING);
        insertResult(1L, EvalResultStatusEnums.MODEL_CALLED);

        assertThat(writer.claim(1L, 0).getStatus()).isEqualTo(EvalResultStatusEnums.SAFETY_EVALUATING.getCode());
        assertThat(writer.claim(1L, 0)).isNotNull();
        assertThat(taskInt("finished_count")).isZero();
        verify(outbox, times(1)).recordResultDetailUpsert(1L);
    }

    @Test
    void missingStatusIsRejectedAsNotReady() {
        insertResult(1L, EvalResultStatusEnums.MODEL_CALLED);
        jdbc.update("UPDATE eval_result_detail SET status = NULL WHERE id = 1");

        assertThatThrownBy(() -> writer.claim(1L, 0)).isInstanceOf(ServiceException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void finishedResultRequiresDefiniteSafetyVerdict() {
        insertTask(2, 0, 0, TaskStatusEnums.RUNNING);
        insertResult(1L, EvalResultStatusEnums.SAFETY_EVALUATING);
        EvalResultDetailDO invalid = outcome(1L, EvalResultStatusEnums.FINISHED);
        invalid.setIsSafe(EvalSafeStatusEnums.UNJUDGED.getCode());

        assertThatThrownBy(() -> writer.complete(invalid)).isInstanceOf(ServiceException.class);

        assertThat(resultMapper.selectById(1L).getStatus()).isEqualTo(EvalResultStatusEnums.SAFETY_EVALUATING.getCode());
        assertThat(taskInt("finished_count")).isZero();
        verifyNoInteractions(outbox);
    }

    @Test
    void duplicateCompletionDoesNotIncreaseProgressTwice() {
        insertTask(2, 0, 0, TaskStatusEnums.RUNNING);
        insertResult(1L, EvalResultStatusEnums.SAFETY_EVALUATING);

        writer.complete(outcome(1L, EvalResultStatusEnums.FINISHED));
        writer.complete(outcome(1L, EvalResultStatusEnums.FINISHED));

        assertThat(taskInt("finished_count")).isEqualTo(1);
        verify(outbox, times(1)).recordResultDetailUpsert(1L);
    }

    @Test
    void stoppedBatchPreventsClaim() {
        insertTask(2, 0, 0, TaskStatusEnums.STOPPED);
        insertResult(1L, EvalResultStatusEnums.MODEL_CALLED);

        assertThat(writer.claim(1L, 0)).isNull();

        assertThat(resultMapper.selectById(1L).getStatus()).isEqualTo(EvalResultStatusEnums.STOPPED.getCode());
        assertThat(taskInt("status")).isEqualTo(TaskStatusEnums.STOPPED.getCode());
        assertThat(taskInt("finished_count")).isZero();
    }

    @Test
    void stopDuringEvaluationCannotBeOverwrittenBySuccess() {
        insertTask(2, 0, 0, TaskStatusEnums.RUNNING);
        insertResult(1L, EvalResultStatusEnums.MODEL_CALLED);
        writer.claim(1L, 0);
        jdbc.update("UPDATE eval_task_detail SET status = ? WHERE id = 20", TaskStatusEnums.STOPPED.getCode());

        writer.complete(outcome(1L, EvalResultStatusEnums.FINISHED));

        EvalResultDetailDO stored = resultMapper.selectById(1L);
        assertThat(stored.getStatus()).isEqualTo(EvalResultStatusEnums.STOPPED.getCode());
        assertThat(stored.getIsSafe()).isEqualTo(EvalSafeStatusEnums.UNJUDGED.getCode());
        assertThat(taskInt("status")).isEqualTo(TaskStatusEnums.STOPPED.getCode());
        assertThat(taskInt("finished_count")).isZero();
    }

    @Test
    void progressWriteFailureRollsBackResultAndAllowsRetry() {
        insertTask(2, 0, 0, TaskStatusEnums.RUNNING);
        insertResult(1L, EvalResultStatusEnums.SAFETY_EVALUATING);
        EvalTaskDetailMapper failingTasks = mock(EvalTaskDetailMapper.class, delegatesTo(taskMapper));
        doReturn(0).when(failingTasks).update(isNull(), any(Wrapper.class));
        SafetyEvaluateResultWriter failingWriter = new SafetyEvaluateResultWriter(
                resultMapper, failingTasks, outbox, transactions);

        assertThatThrownBy(() -> failingWriter.complete(outcome(1L, EvalResultStatusEnums.FINISHED)))
                .isInstanceOf(ServiceException.class);

        assertThat(resultMapper.selectById(1L).getStatus()).isEqualTo(EvalResultStatusEnums.SAFETY_EVALUATING.getCode());
        assertThat(taskInt("finished_count")).isZero();
        writer.complete(outcome(1L, EvalResultStatusEnums.FINISHED));
        assertThat(taskInt("finished_count")).isEqualTo(1);
        assertThat(resultMapper.selectById(1L).getStatus()).isEqualTo(EvalResultStatusEnums.FINISHED.getCode());
    }

    @Test
    void outboxFailureRollsBackBusinessWrites() {
        insertTask(2, 0, 0, TaskStatusEnums.RUNNING);
        insertResult(1L, EvalResultStatusEnums.SAFETY_EVALUATING);
        doThrow(new IllegalStateException("outbox unavailable")).when(outbox).recordResultDetailUpsert(1L);

        assertThatThrownBy(() -> writer.complete(outcome(1L, EvalResultStatusEnums.FINISHED)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(resultMapper.selectById(1L).getStatus()).isEqualTo(EvalResultStatusEnums.SAFETY_EVALUATING.getCode());
        assertThat(taskInt("finished_count")).isZero();
    }

    @Test
    void concurrentSamplesDoNotLoseProgress() throws Exception {
        insertTask(2, 0, 0, TaskStatusEnums.RUNNING);
        insertResult(1L, EvalResultStatusEnums.SAFETY_EVALUATING);
        insertResult(2L, EvalResultStatusEnums.SAFETY_EVALUATING);

        completeConcurrently(1L, 2L);

        assertThat(taskInt("finished_count")).isEqualTo(2);
        assertThat(taskInt("status")).isEqualTo(TaskStatusEnums.COMPLETED.getCode());
    }

    @Test
    void concurrentDuplicateCompletionCountsOnlyOnce() throws Exception {
        insertTask(2, 0, 0, TaskStatusEnums.RUNNING);
        insertResult(1L, EvalResultStatusEnums.SAFETY_EVALUATING);

        completeConcurrently(1L, 1L);

        assertThat(taskInt("finished_count")).isEqualTo(1);
        assertThat(taskInt("status")).isEqualTo(TaskStatusEnums.RUNNING.getCode());
        verify(outbox, times(1)).recordResultDetailUpsert(1L);
    }

    private void completeConcurrently(Long first, Long second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = CompletableFuture.runAsync(() -> awaitAndComplete(start, first), executor);
            var two = CompletableFuture.runAsync(() -> awaitAndComplete(start, second), executor);
            start.countDown();
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        }
    }

    private void awaitAndComplete(CountDownLatch start, Long id) {
        try {
            start.await();
            writer.complete(outcome(id, EvalResultStatusEnums.FINISHED));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

}
