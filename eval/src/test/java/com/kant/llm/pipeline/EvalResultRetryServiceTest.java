package com.kant.llm.pipeline;

import com.alibaba.fastjson2.JSON;
import com.kant.ai.infra.client.ModelResponse;
import com.kant.framework.exception.ServiceException;
import com.kant.framework.mq.MessageWrapper;
import com.kant.framework.mq.producer.DelegatingTransactionListener;
import com.kant.framework.mq.producer.MessageQueueProducer;
import com.kant.framework.mq.producer.MessageSendRequest;
import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.common.enums.TaskStatusEnums;
import com.kant.llm.eval.pipeline.record.ModelCallMessage;
import com.kant.llm.eval.pipeline.record.SafetyEvaluateMessage;
import com.kant.llm.eval.pipeline.service.ResultExecution;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.task.dto.req.RetryEvalResultRequest;
import com.kant.llm.eval.task.dto.resp.RetryEvalResultVO;
import com.kant.llm.eval.task.mq.EvalResultRetryTransactionChecker;
import com.kant.llm.eval.task.service.EvalResultRetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EvalResultRetryServiceTest extends ResultExecutionTestDatabase {
    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock taskLock = mock(RLock.class);
    private final RLock resultLock = mock(RLock.class);
    private final MessageQueueProducer producer = mock(MessageQueueProducer.class);
    private final List<MessageSendRequest> sent = new ArrayList<>();
    private EvalResultRetryService retries;

    @BeforeEach
    void setUpRetryService() throws InterruptedException {
        when(redisson.getLock("aigc-eval:eval-task:operation:10")).thenReturn(taskLock);
        when(redisson.getLock(ResultExecution.lockKey(1L))).thenReturn(resultLock);
        when(taskLock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true);
        when(resultLock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true);
        when(taskLock.isHeldByCurrentThread()).thenReturn(true);
        when(resultLock.isHeldByCurrentThread()).thenReturn(true);
        doAnswer(invocation -> {
            MessageSendRequest message = invocation.getArgument(0);
            sent.add(message);
            // 模拟 RocketMQ 的半消息及真实数据库本地事务，提交后才允许消费。
            transactions.executeWithoutResult(tx -> message.getLocalTransaction().accept(null));
            return null;
        }).when(producer).sendInTransaction(any());
        retries = new EvalResultRetryService(resultMapper, taskMapper, retryMapper,
                outbox, redisson, producer, transactions);
    }

    @Test
    void autoReusesOutputAndReopensCompletedBatch() {
        completedSample();

        RetryEvalResultVO accepted = retries.retry(request("auto", RetryEvalResultRequest.Mode.AUTO));

        assertThat(accepted.executionVersion()).isEqualTo(1);
        assertThat(accepted.startStage()).isEqualTo("SAFETY_EVALUATE");
        EvalResultDetailDO result = resultMapper.selectById(1L);
        assertThat(result.getModelOutput()).isEqualTo("model output");
        assertThat(result.getStatus()).isEqualTo(EvalResultStatusEnums.MODEL_CALLED.getCode());
        assertThat(result.getProgressCounted()).isFalse();
        assertThat(taskInt("finished_count")).isZero();
        assertThat(taskInt("status")).isEqualTo(TaskStatusEnums.RUNNING.getCode());
        assertThat(jdbc.queryForObject("SELECT end_time FROM eval_task_detail WHERE id=20", Object.class)).isNull();
        assertThat(sent.getFirst().getBody()).isInstanceOf(SafetyEvaluateMessage.class);
    }

    @Test
    void fullRetryClearsOutputAndStartsAtModel() {
        completedSample();
        jdbc.update("UPDATE eval_result_detail SET raw_response = '{}', latency = 100 WHERE id=1");

        RetryEvalResultVO accepted = retries.retry(request("full", RetryEvalResultRequest.Mode.FULL));

        assertThat(accepted.startStage()).isEqualTo("MODEL_CALL");
        EvalResultDetailDO result = resultMapper.selectById(1L);
        assertThat(result.getModelOutput()).isNull();
        assertThat(result.getRawResponse()).isNull();
        assertThat(result.getLatency()).isNull();
        assertThat(result.getStatus()).isEqualTo(EvalResultStatusEnums.PENDING.getCode());
        assertThat(sent.getFirst().getBody()).isInstanceOf(ModelCallMessage.class);
    }

    @Test
    void failedModelWithoutOutputRetriesFullChainAndReversesFailureCount() {
        insertTask(1, 1, 1, TaskStatusEnums.ERROR);
        insertResult(1L, EvalResultStatusEnums.FAILED);
        jdbc.update("UPDATE eval_result_detail SET progress_counted=TRUE, model_output=NULL WHERE id=1");

        RetryEvalResultVO accepted = retries.retry(request("failed", RetryEvalResultRequest.Mode.AUTO));

        assertThat(accepted.startStage()).isEqualTo("MODEL_CALL");
        assertThat(taskInt("finished_count")).isZero();
        assertThat(taskInt("failed_count")).isZero();
    }

    @Test
    void interruptedAttemptDoesNotSubtractAnotherSamplesProgress() {
        insertTask(2, 1, 0, TaskStatusEnums.RUNNING);
        insertResult(1L, EvalResultStatusEnums.SAFETY_EVALUATING);

        retries.retry(request("interrupted", RetryEvalResultRequest.Mode.AUTO));

        assertThat(taskInt("finished_count")).isEqualTo(1);
        assertThat(resultMapper.selectById(1L).getExecutionVersion()).isEqualTo(1);
    }

    @Test
    void sameRequestReplaysEvenAfterCompletion() {
        completedSample();
        RetryEvalResultRequest request = request("idempotent", RetryEvalResultRequest.Mode.AUTO);
        RetryEvalResultVO first = retries.retry(request);
        EvalResultDetailDO result = writer.claim(1L, 1);
        result.setStatus(EvalResultStatusEnums.FINISHED.getCode());
        result.setIsSafe(1);
        writer.complete(result);

        assertThat(retries.retry(request)).isEqualTo(first);
        assertThat(taskInt("finished_count")).isEqualTo(1);
        assertThat(sent).hasSize(1);
    }

    @Test
    void differentRequestWhileQueuedAliasesExistingAttempt() {
        completedSample();
        retries.retry(request("first", RetryEvalResultRequest.Mode.AUTO));
        RetryEvalResultRequest alias = request("second", RetryEvalResultRequest.Mode.FULL);

        RetryEvalResultVO accepted = retries.retry(alias);

        assertThat(accepted.executionVersion()).isEqualTo(1);
        assertThat(accepted.startStage()).isEqualTo("SAFETY_EVALUATE");
        assertThat(sent).hasSize(1);
        jdbc.update("UPDATE eval_result_detail SET status=8, progress_counted=TRUE WHERE id=1");
        assertThat(retries.retry(alias)).isEqualTo(accepted);
        assertThat(sent).hasSize(1);
    }

    @Test
    void requestIdCannotBeReusedWithDifferentMode() {
        completedSample();
        retries.retry(request("same", RetryEvalResultRequest.Mode.AUTO));

        assertThatThrownBy(() -> retries.retry(request("same", RetryEvalResultRequest.Mode.FULL)))
                .isInstanceOf(ServiceException.class);
        assertThat(sent).hasSize(1);
    }

    @Test
    void oldSafetyMessageAndOldWriteCannotAffectNewAttempt() {
        completedSample();
        EvalResultDetailDO stale = outcome(1L, EvalResultStatusEnums.FINISHED);
        retries.retry(request("new", RetryEvalResultRequest.Mode.AUTO));
        writer.claim(1L, 1);

        assertThat(writer.claim(1L, 0)).isNull();
        writer.complete(stale);

        assertThat(taskInt("finished_count")).isZero();
        assertThat(resultMapper.selectById(1L).getStatus()).isEqualTo(EvalResultStatusEnums.SAFETY_EVALUATING.getCode());
        assertThat(resultMapper.selectById(1L).getExecutionVersion()).isEqualTo(1);
    }

    @Test
    void oldModelResultCannotOverwriteNewOutput() {
        completedSample();
        EvalResultDetailDO stale = resultMapper.selectById(1L);
        retries.retry(request("new-model", RetryEvalResultRequest.Mode.FULL));
        writer.claimModel(1L, 1);

        assertThat(writer.claimModel(1L, 0)).isNull();
        assertThat(writer.completeModel(stale, ModelResponse.builder().respContent("stale").elapsed(1L).build())).isFalse();
        assertThat(resultMapper.selectById(1L).getModelOutput()).isNull();
    }

    @Test
    void fullRetryCompletesEndToEndWithSingleCount() {
        completedSample();
        retries.retry(request("chain", RetryEvalResultRequest.Mode.FULL));
        EvalResultDetailDO model = writer.claimModel(1L, 1);

        assertThat(writer.completeModel(model, ModelResponse.builder().respContent("new output").elapsed(1L).build())).isTrue();
        EvalResultDetailDO safety = writer.claim(1L, 1);
        safety.setStatus(EvalResultStatusEnums.FINISHED.getCode());
        safety.setIsSafe(2);
        writer.complete(safety);
        writer.complete(safety);

        assertThat(taskInt("finished_count")).isEqualTo(1);
        assertThat(taskInt("status")).isEqualTo(TaskStatusEnums.COMPLETED.getCode());
        assertThat(resultMapper.selectById(1L).getModelOutput()).isEqualTo("new output");
        assertThat(resultMapper.selectById(1L).getIsSafe()).isEqualTo(2);
    }

    @Test
    void activeExecutionIsRejectedWithoutChangingCounters() throws InterruptedException {
        completedSample();
        when(resultLock.tryLock(0, TimeUnit.SECONDS)).thenReturn(false);

        assertThatThrownBy(() -> retries.retry(request("busy", RetryEvalResultRequest.Mode.AUTO)))
                .isInstanceOf(ServiceException.class);

        assertThat(taskInt("finished_count")).isEqualTo(1);
        verifyNoInteractions(producer);
        verify(taskLock).unlock();
        verify(resultLock, never()).unlock();
    }

    @Test
    void stoppedBatchIsNotReopened() {
        insertTask(1, 0, 0, TaskStatusEnums.STOPPED);
        insertResult(1L, EvalResultStatusEnums.STOPPED);

        assertThatThrownBy(() -> retries.retry(request("stopped", RetryEvalResultRequest.Mode.AUTO)))
                .isInstanceOf(ServiceException.class);

        assertThat(taskInt("status")).isEqualTo(TaskStatusEnums.STOPPED.getCode());
        verifyNoInteractions(producer);
    }

    @Test
    void historicalBatchCannotBeReopened() {
        completedSample();
        jdbc.update("INSERT INTO eval_task_detail(id,task_id,status) VALUES(21,10,4)");

        assertThatThrownBy(() -> retries.retry(request("historical", RetryEvalResultRequest.Mode.AUTO)))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(producer);
    }

    @Test
    void outboxFailureRollsBackResetCountersAndRetryReceipt() {
        completedSample();
        doThrow(new IllegalStateException("outbox failure")).when(outbox).recordResultDetailUpsert(1L);

        assertThatThrownBy(() -> retries.retry(request("rollback", RetryEvalResultRequest.Mode.AUTO)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(taskInt("finished_count")).isEqualTo(1);
        assertThat(taskInt("status")).isEqualTo(TaskStatusEnums.COMPLETED.getCode());
        assertThat(resultMapper.selectById(1L).getExecutionVersion()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eval_result_retry", Integer.class)).isZero();
    }

    @Test
    void brokerFailureBeforeLocalTransactionLeavesResultUntouched() {
        completedSample();
        doThrow(new IllegalStateException("broker unavailable")).when(producer).sendInTransaction(any());

        assertThatThrownBy(() -> retries.retry(request("broker", RetryEvalResultRequest.Mode.AUTO)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(taskInt("finished_count")).isEqualTo(1);
        assertThat(resultMapper.selectById(1L).getExecutionVersion()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eval_result_retry", Integer.class)).isZero();
    }

    @Test
    void lostResponseAfterLocalCommitReplaysOriginalReceipt() {
        completedSample();
        RetryEvalResultRequest request = request("lost-response", RetryEvalResultRequest.Mode.AUTO);
        doAnswer(invocation -> {
            MessageSendRequest message = invocation.getArgument(0);
            transactions.executeWithoutResult(tx -> message.getLocalTransaction().accept(null));
            throw new IllegalStateException("response lost");
        }).when(producer).sendInTransaction(any());

        assertThatThrownBy(() -> retries.retry(request)).isInstanceOf(IllegalStateException.class);
        RetryEvalResultVO replay = retries.retry(request);

        assertThat(replay.executionVersion()).isEqualTo(1);
        assertThat(taskInt("finished_count")).isZero();
        verify(producer, times(1)).sendInTransaction(any());
    }

    @Test
    void concurrentRetryCannotResetSameSampleTwice() throws Exception {
        completedSample();
        ReentrantLock mutualExclusion = new ReentrantLock();
        when(taskLock.tryLock(0, TimeUnit.SECONDS)).thenAnswer(ignored -> mutualExclusion.tryLock());
        when(taskLock.isHeldByCurrentThread()).thenAnswer(ignored -> mutualExclusion.isHeldByCurrentThread());
        doAnswer(ignored -> { mutualExclusion.unlock(); return null; }).when(taskLock).unlock();
        CountDownLatch localEntered = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            localEntered.countDown();
            if (!allowCommit.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test transaction timeout");
            }
            MessageSendRequest message = invocation.getArgument(0);
            transactions.executeWithoutResult(tx -> message.getLocalTransaction().accept(null));
            return null;
        }).when(producer).sendInTransaction(any());

        try (var executor = Executors.newSingleThreadExecutor()) {
            CompletableFuture<RetryEvalResultVO> first = CompletableFuture.supplyAsync(
                    () -> retries.retry(request("concurrent-one", RetryEvalResultRequest.Mode.AUTO)), executor);
            try {
                assertThat(localEntered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> retries.retry(request("concurrent-two", RetryEvalResultRequest.Mode.FULL)))
                        .isInstanceOf(ServiceException.class);
            } finally {
                allowCommit.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS).executionVersion()).isEqualTo(1);
        }
        assertThat(taskInt("finished_count")).isZero();
        assertThat(resultMapper.selectById(1L).getExecutionVersion()).isEqualTo(1);
        verify(producer, times(1)).sendInTransaction(any());
    }

    @Test
    void stopBeforeLocalCommitRollsBackRetry() {
        completedSample();
        doAnswer(invocation -> {
            jdbc.update("UPDATE eval_task_detail SET status=6 WHERE id=20");
            MessageSendRequest message = invocation.getArgument(0);
            transactions.executeWithoutResult(tx -> message.getLocalTransaction().accept(null));
            return null;
        }).when(producer).sendInTransaction(any());

        assertThatThrownBy(() -> retries.retry(request("stop-race", RetryEvalResultRequest.Mode.AUTO)))
                .isInstanceOf(ServiceException.class);

        assertThat(taskInt("status")).isEqualTo(TaskStatusEnums.STOPPED.getCode());
        assertThat(resultMapper.selectById(1L).getExecutionVersion()).isZero();
    }

    @ParameterizedTest
    @EnumSource(RetryEvalResultRequest.Mode.class)
    void transactionCheckUsesPersistedReceiptAfterSerialization(RetryEvalResultRequest.Mode mode) {
        completedSample();
        retries.retry(request("check", mode));
        DelegatingTransactionListener listener = mock(DelegatingTransactionListener.class);
        EvalResultRetryTransactionChecker checker = new EvalResultRetryTransactionChecker(retryMapper, listener);
        MessageWrapper<?> wire = JSON.parseObject(JSON.toJSONString(
                MessageWrapper.builder().body(sent.getFirst().getBody()).build()), MessageWrapper.class);

        assertThat(checker.check(wire, mode == RetryEvalResultRequest.Mode.FULL)).isTrue();
        jdbc.update("DELETE FROM eval_result_retry");
        assertThat(checker.check(wire, mode == RetryEvalResultRequest.Mode.FULL)).isFalse();
    }

    private void completedSample() {
        insertTask(1, 1, 0, TaskStatusEnums.COMPLETED);
        insertResult(1L, EvalResultStatusEnums.FINISHED);
        jdbc.update("UPDATE eval_result_detail SET progress_counted=TRUE WHERE id=1");
        jdbc.update("UPDATE eval_task_detail SET end_time=CURRENT_TIMESTAMP WHERE id=20");
    }

    private RetryEvalResultRequest request(String id, RetryEvalResultRequest.Mode mode) {
        RetryEvalResultRequest request = new RetryEvalResultRequest();
        request.setResultDetailId(1L);
        request.setRequestId(id);
        request.setMode(mode);
        return request;
    }
}
