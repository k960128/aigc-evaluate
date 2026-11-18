package com.kant.llm.pipeline;

import com.kant.ai.infra.client.ModelClientStrategy;
import com.kant.ai.infra.client.ModelClientStrategyFactory;
import com.kant.ai.infra.client.ModelResponse;
import com.kant.framework.exception.ServiceException;
import com.kant.framework.mq.producer.MessageQueueProducer;
import com.kant.framework.mq.producer.MessageSendRequest;
import com.kant.llm.common.enums.EvalResultStatusEnums;
import com.kant.llm.eval.basesource.dao.entity.ModelInfoDO;
import com.kant.llm.eval.basesource.service.MockModelOutputContentService;
import com.kant.llm.eval.basesource.service.ModelInfoService;
import com.kant.llm.eval.pipeline.record.ModelCallMessage;
import com.kant.llm.eval.pipeline.record.SafetyEvaluateMessage;
import com.kant.llm.eval.pipeline.record.EvalPipelineNodeRecorder;
import com.kant.llm.eval.pipeline.ratelimit.ModelCallPermit;
import com.kant.llm.eval.pipeline.ratelimit.ModelCallRateLimiter;
import com.kant.llm.eval.pipeline.service.ModelCallExecutionV1Service;
import com.kant.llm.eval.pipeline.service.ResultExecution;
import com.kant.llm.eval.pipeline.service.SafetyEvaluateResultWriter;
import com.kant.llm.eval.task.dao.entity.EvalResultDetailDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModelCallExecutionV1ServiceTest {
    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final ModelInfoService models = mock(ModelInfoService.class);
    private final MessageQueueProducer producer = mock(MessageQueueProducer.class);
    private final ModelCallRateLimiter limiter = mock(ModelCallRateLimiter.class);
    private final SafetyEvaluateResultWriter writer = mock(SafetyEvaluateResultWriter.class);
    private final EvalPipelineNodeRecorder recorder = mock(EvalPipelineNodeRecorder.class);
    private final ModelClientStrategyFactory strategies = mock(ModelClientStrategyFactory.class);
    private final MockModelOutputContentService mocks = mock(MockModelOutputContentService.class);
    private final ModelClientStrategy strategy = mock(ModelClientStrategy.class);
    private final ModelCallPermit permit = mock(ModelCallPermit.class);
    private final ModelCallMessage message = ModelCallMessage.builder().resultDetailId(1L).taskId(10L)
            .taskDetailId(20L).modelId(5L).sampleId(30L).executionVersion(2).build();
    private final EvalResultDetailDO result = EvalResultDetailDO.builder().id(1L).taskId(10L)
            .taskDetailId(20L).sampleId(30L).executionVersion(2)
            .status(EvalResultStatusEnums.MODEL_RUNNING.getCode()).build();
    private ModelCallExecutionV1Service service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ModelCallExecutionV1Service(redisson, models, producer, limiter,
                writer, recorder, strategies, mocks);
        when(redisson.getLock(ResultExecution.lockKey(1L))).thenReturn(lock);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(writer.claimModel(1L, 2)).thenReturn(result);
        when(models.getById(5L)).thenReturn(ModelInfoDO.builder().id(5L).model("test")
                .manufacturerCode("MOCK").build());
        when(limiter.acquire(any())).thenReturn(permit);
        when(strategies.getStrategy(any())).thenReturn(strategy);
        when(strategy.call(any())).thenReturn(ModelResponse.builder().respContent("output").elapsed(1L).build());
        when(writer.completeModel(eq(result), any())).thenReturn(true);
    }

    @Test
    void completedOutputOnlyResendsSafetyMessageWithSameVersion() {
        result.setStatus(EvalResultStatusEnums.MODEL_CALLED.getCode());

        service.submitExecute(message);

        verifyNoInteractions(models, strategies, limiter);
        ArgumentCaptor<MessageSendRequest> sent = ArgumentCaptor.forClass(MessageSendRequest.class);
        verify(producer).send(sent.capture());
        assertThat(((SafetyEvaluateMessage) sent.getValue().getBody()).getExecutionVersion()).isEqualTo(2);
    }

    @Test
    void staleMessageDoesNotCallModel() {
        when(writer.claimModel(1L, 2)).thenReturn(null);

        service.submitExecute(message);

        verifyNoInteractions(models, strategies, limiter, producer);
    }

    @Test
    void successfulModelCallUsesWatchdogAndReleasesPermit() throws InterruptedException {
        service.submitExecute(message);

        verify(writer).completeModel(eq(result), any());
        verify(producer).send(any());
        verify(permit).release();
        verify(lock).unlock();
        verify(lock).tryLock(10, TimeUnit.SECONDS);
        verify(lock, never()).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void permitFailurePropagatesOriginalCauseWithoutNullRelease() {
        IllegalStateException failure = new IllegalStateException("rate limited");
        when(limiter.acquire(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.submitExecute(message)).isInstanceOf(ServiceException.class).hasRootCause(failure);

        verifyNoInteractions(permit, strategy, producer);
        verify(writer).recordFailure(1L, 2, failure);
    }

    @Test
    void modelExceptionTriggersMqRetryAndReleasesPermit() {
        IllegalStateException failure = new IllegalStateException("model unavailable");
        when(strategy.call(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.submitExecute(message)).isInstanceOf(ServiceException.class).hasRootCause(failure);

        verify(writer, never()).completeModel(any(), any());
        verifyNoInteractions(producer);
        verify(permit).release();
    }
}
