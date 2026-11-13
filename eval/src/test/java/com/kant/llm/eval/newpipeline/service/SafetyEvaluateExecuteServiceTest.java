package com.kant.llm.eval.newpipeline.service;

import com.kant.framework.exception.ServiceException;
import com.kant.llm.common.convention.L1EvalContext;
import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.common.enums.EvalSafeStatusEnums;
import com.kant.llm.common.enums.SafetyEvaluateStatusEnums;
import com.kant.llm.eval.fingerprint.engine.NegativeFingerprintACTrieManager;
import com.kant.llm.eval.fingerprint.service.KnowledgePositiveFingerprintService;
import com.kant.llm.eval.newpipeline.engine.L2SafetyEvaluateExecutorEngine;
import com.kant.llm.eval.newpipeline.engine.L3SafetyEvaluateExecutorEngine;
import com.kant.llm.eval.newpipeline.record.L2EvaluateExecuteRecord;
import com.kant.llm.eval.newpipeline.record.L3JudgeResult;
import com.kant.llm.eval.pipeline.mq.message.SafetyEvaluateMessage;
import com.kant.llm.eval.pipeline.service.EvalPipelineNodeRecorder;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SafetyEvaluateExecuteServiceTest {
    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final SafetyEvaluateResultWriter writer = mock(SafetyEvaluateResultWriter.class);
    private final EvalPipelineNodeRecorder recorder = mock(EvalPipelineNodeRecorder.class);
    private final L2SafetyEvaluateExecutorEngine l2 = mock(L2SafetyEvaluateExecutorEngine.class);
    private final L3SafetyEvaluateExecutorEngine l3 = mock(L3SafetyEvaluateExecutorEngine.class);
    private final NegativeFingerprintACTrieManager negative = mock(NegativeFingerprintACTrieManager.class);
    private final KnowledgePositiveFingerprintService positive = mock(KnowledgePositiveFingerprintService.class);
    private final L1EvalContext negativeContext = mock(L1EvalContext.class);
    private final SafetyEvaluateMessage message = SafetyEvaluateMessage.builder().resultDetailId(1L).build();
    private final EvalResultDetailDO result = EvalResultDetailDO.builder()
            .id(1L).taskId(10L).taskDetailId(20L).riskDetailsId(40L)
            .modelOutput("model output").status(EvalResultStatusEnums.SAFETY_EVALUATING.getCode())
            .isSafe(EvalSafeStatusEnums.UNJUDGED.getCode()).build();
    private SafetyEvaluateExecuteService service;

    @BeforeEach
    void setUp() throws InterruptedException {
        service = new SafetyEvaluateExecuteService(redisson, writer, recorder, l2, l3, negative, positive);
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(writer.claim(1L, 0)).thenReturn(result);
        when(positive.validateStandardRefusal(anyLong(), anyString())).thenReturn(false);
        when(negative.match(anyLong(), anyString())).thenReturn(negativeContext);
        when(recorder.startNode(anyLong(), anyLong(), any(), any(), any())).thenReturn(100L);
        when(l2.execute(any())).thenReturn(l2Result(SafetyEvaluateStatusEnums.BLURRY));
        when(l3.execute(any(), any())).thenReturn(new L3JudgeResult("safe", SafetyEvaluateStatusEnums.SAFE, null));
    }

    @Test
    void positiveFingerprintPersistsSafeVerdictAndUsesWatchdog() throws InterruptedException {
        when(positive.validateStandardRefusal(40L, "model output")).thenReturn(true);

        service.submitExecute(message);

        assertThat(result.getIsSafe()).isEqualTo(EvalSafeStatusEnums.SAFE.getCode());
        assertThat(result.getStatus()).isEqualTo(EvalResultStatusEnums.FINISHED.getCode());
        verify(writer).complete(result);
        verifyNoInteractions(l2, l3, negative);
        verify(lock).tryLock(10, TimeUnit.SECONDS);
        verify(lock, never()).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        verify(lock).unlock();
    }

    @Test
    void negativeFingerprintProducesViolation() {
        when(negativeContext.isL1Blocked()).thenReturn(true);

        service.submitExecute(message);

        assertThat(result.getIsSafe()).isEqualTo(EvalSafeStatusEnums.VIOLATION.getCode());
        assertThat(result.getStatus()).isEqualTo(EvalResultStatusEnums.FINISHED.getCode());
        verify(writer).complete(result);
        verifyNoInteractions(l2, l3);
    }

    @ParameterizedTest
    @EnumSource(value = SafetyEvaluateStatusEnums.class, names = {"SAFE", "VIOLATION"})
    void l2DefiniteVerdictShortCircuitsL3(SafetyEvaluateStatusEnums verdict) {
        when(l2.execute(any())).thenReturn(l2Result(verdict));

        service.submitExecute(message);

        assertThat(result.getIsSafe()).isEqualTo(verdict == SafetyEvaluateStatusEnums.SAFE
                ? EvalSafeStatusEnums.SAFE.getCode() : EvalSafeStatusEnums.VIOLATION.getCode());
        assertThat(result.getStatus()).isEqualTo(EvalResultStatusEnums.FINISHED.getCode());
        verify(writer).complete(result);
        verifyNoInteractions(l3);
    }

    @ParameterizedTest
    @EnumSource(value = SafetyEvaluateStatusEnums.class, names = {"BLURRY", "UNKNOWN"})
    void uncertainL3VerdictRequiresManualReview(SafetyEvaluateStatusEnums verdict) {
        when(l3.execute(any(), any())).thenReturn(new L3JudgeResult("uncertain", verdict, null));

        service.submitExecute(message);

        assertThat(result.getStatus()).isEqualTo(EvalResultStatusEnums.MANUAL_REVIEWED.getCode());
        assertThat(result.getIsSafe()).isEqualTo(EvalSafeStatusEnums.UNJUDGED.getCode());
        verify(writer).complete(result);
        verify(recorder, never()).failNode(anyLong(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = SafetyEvaluateStatusEnums.class, names = {"SAFE", "VIOLATION"})
    void definiteL3VerdictCanComplete(SafetyEvaluateStatusEnums verdict) {
        when(l3.execute(any(), any())).thenReturn(new L3JudgeResult("decided", verdict, null));

        service.submitExecute(message);

        assertThat(result.getStatus()).isEqualTo(EvalResultStatusEnums.FINISHED.getCode());
        assertThat(result.getIsSafe()).isEqualTo(verdict == SafetyEvaluateStatusEnums.SAFE
                ? EvalSafeStatusEnums.SAFE.getCode() : EvalSafeStatusEnums.VIOLATION.getCode());
        verify(writer).complete(result);
    }

    @Test
    void l3FailurePropagatesForMqRetryAndDoesNotComplete() {
        IllegalStateException failure = new IllegalStateException("judge timeout");
        when(l3.execute(any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> service.submitExecute(message))
                .isInstanceOf(ServiceException.class).hasRootCause(failure);

        verify(writer, never()).complete(any());
        verify(recorder).failNode(100L, failure, null, null);
        verify(lock).unlock();
    }

    @Test
    void emptyJudgeResultIsNotSuccessfulCompletion() {
        when(l3.execute(any(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.submitExecute(message)).isInstanceOf(ServiceException.class);

        verify(writer, never()).complete(any());
        verify(recorder).failNode(eq(100L), any(Exception.class), isNull(), isNull());
    }

    @Test
    void duplicateTerminalMessageSkipsAllEvaluation() {
        when(writer.claim(1L, 0)).thenReturn(null);

        service.submitExecute(message);

        verifyNoInteractions(positive, negative, l2, l3, recorder);
        verify(writer, never()).complete(any());
        verify(lock).unlock();
    }

    @Test
    void lockContentionRequestsRetryInsteadOfAcknowledging() throws InterruptedException {
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(false);

        assertThatThrownBy(() -> service.submitExecute(message)).isInstanceOf(ServiceException.class);

        verifyNoInteractions(writer, positive, negative, l2, l3);
        verify(lock, never()).unlock();
    }

    @Test
    void lostLockPreventsCompletion() {
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        assertThatThrownBy(() -> service.submitExecute(message)).isInstanceOf(ServiceException.class);

        verify(writer, never()).complete(any());
        verify(lock, never()).unlock();
    }

    @Test
    void stopBetweenStagesSkipsRemainingRemoteCalls() {
        when(writer.isTaskStopped(20L)).thenReturn(true);

        service.submitExecute(message);

        verifyNoInteractions(l2, l3);
        // 最终写入仍需由事务内的批次状态检查决定是否落为 STOPPED。
        verify(writer).complete(result);
    }

    private L2EvaluateExecuteRecord l2Result(SafetyEvaluateStatusEnums status) {
        return new L2EvaluateExecuteRecord(status, 0.5, List.of(), 40L);
    }
}
