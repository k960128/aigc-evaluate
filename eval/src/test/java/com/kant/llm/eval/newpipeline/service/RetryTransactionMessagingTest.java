package com.kant.llm.eval.newpipeline.service;

import com.alibaba.fastjson2.JSON;
import com.kant.framework.exception.ServiceException;
import com.kant.framework.mq.MessageWrapper;
import com.kant.framework.mq.producer.DelegatingTransactionListener;
import com.kant.framework.mq.producer.MessageSendRequest;
import com.kant.framework.mq.producer.RocketMQProducerAdapter;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.eval.pipeline.mq.message.SafetyEvaluateMessage;
import com.kant.llm.eval.task.dao.entity.EvalResultRetryDO;
import com.kant.llm.eval.task.mq.EvalResultRetryTransactionChecker;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetryTransactionMessagingTest extends ResultExecutionTestDatabase {
    @Test
    void adapterAcceptsConfirmedCommit() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        DelegatingTransactionListener listener = mock(DelegatingTransactionListener.class);
        TransactionSendResult result = new TransactionSendResult();
        result.setSendStatus(SendStatus.SEND_OK);
        result.setLocalTransactionState(LocalTransactionState.COMMIT_MESSAGE);
        when(template.sendMessageInTransaction(anyString(), any(), isNull())).thenReturn(result);

        new RocketMQProducerAdapter(template, listener).sendInTransaction(
                MessageSendRequest.builder().topic("test").localTransaction(ignored -> {}).build());

        verify(listener).removeLocalTransaction(anyString());
    }

    @Test
    void adapterRejectsUnconfirmedBrokerSend() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        DelegatingTransactionListener listener = mock(DelegatingTransactionListener.class);
        TransactionSendResult result = new TransactionSendResult();
        result.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
        result.setLocalTransactionState(LocalTransactionState.COMMIT_MESSAGE);
        when(template.sendMessageInTransaction(anyString(), any(), isNull())).thenReturn(result);

        assertThatThrownBy(() -> new RocketMQProducerAdapter(template, listener)
                .sendInTransaction(MessageSendRequest.builder().topic("test").localTransaction(ignored -> {}).build()))
                .isInstanceOf(ServiceException.class);
    }

    @ParameterizedTest
    @EnumSource(value = LocalTransactionState.class, names = {"ROLLBACK_MESSAGE", "UNKNOW"})
    void adapterRejectsUncommittedLocalTransaction(LocalTransactionState state) {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        DelegatingTransactionListener listener = mock(DelegatingTransactionListener.class);
        TransactionSendResult result = new TransactionSendResult();
        result.setSendStatus(SendStatus.SEND_OK);
        result.setLocalTransactionState(state);
        when(template.sendMessageInTransaction(anyString(), any(), isNull())).thenReturn(result);
        RocketMQProducerAdapter adapter = new RocketMQProducerAdapter(template, listener);

        assertThatThrownBy(() -> adapter.sendInTransaction(MessageSendRequest.builder().topic("test")
                .localTransaction(ignored -> {}).build())).isInstanceOf(ServiceException.class);

        verify(listener).removeLocalTransaction(anyString());
    }

    @Test
    void brokerFailureRemovesInMemoryCallback() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        DelegatingTransactionListener listener = mock(DelegatingTransactionListener.class);
        when(template.sendMessageInTransaction(anyString(), any(), isNull()))
                .thenThrow(new IllegalStateException("broker failure"));

        assertThatThrownBy(() -> new RocketMQProducerAdapter(template, listener)
                .sendInTransaction(MessageSendRequest.builder().topic("test").localTransaction(ignored -> {}).build()))
                .isInstanceOf(IllegalStateException.class);

        verify(listener).removeLocalTransaction(anyString());
    }

    @Test
    void serializedBrokerCheckFindsCommittedReceipt() {
        DelegatingTransactionListener listener = new DelegatingTransactionListener();
        new EvalResultRetryTransactionChecker(retryMapper, listener);
        retryMapper.insert(EvalResultRetryDO.builder().id(1L).requestId("committed")
                .resultDetailId(10L).taskDetailId(20L).executionVersion(2)
                .mode("AUTO").startStage("SAFETY_EVALUATE").build());
        byte[] payload = JSON.toJSONBytes(MessageWrapper.builder().body(SafetyEvaluateMessage.builder()
                .resultDetailId(10L).executionVersion(2).retryRequestId("committed").build()).build());

        assertThat(listener.checkLocalTransaction(MessageBuilder.withPayload(payload)
                .setHeader("TRANSACTION_TOPIC", EvalMqTopics.SAFETY_EVALUATE_TOPIC).build()))
                .isEqualTo(RocketMQLocalTransactionState.COMMIT);
        jdbc.update("DELETE FROM eval_result_retry");
        assertThat(listener.checkLocalTransaction(MessageBuilder.withPayload(payload)
                .setHeader("TRANSACTION_TOPIC", EvalMqTopics.SAFETY_EVALUATE_TOPIC).build()))
                .isEqualTo(RocketMQLocalTransactionState.ROLLBACK);
    }

    @Test
    void localCallbackFailureActuallyRollsBackAndRequestsMessageRollback() {
        DelegatingTransactionListener listener = new DelegatingTransactionListener();
        ReflectionTestUtils.setField(listener, "transactionManager", transactions.getTransactionManager());
        listener.registerLocalTransaction("rollback", ignored -> {
            jdbc.update("INSERT INTO eval_result_retry(id,request_id,result_detail_id,task_detail_id,execution_version) "
                    + "VALUES(1,'failed',10,20,1)");
            throw new IllegalStateException("local failure");
        });

        assertThat(listener.executeLocalTransaction(MessageBuilder.withPayload("unused")
                .setHeader("TRANSACTION_CONTEXT_ID", "rollback").build(), null))
                .isEqualTo(RocketMQLocalTransactionState.ROLLBACK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eval_result_retry", Integer.class)).isZero();
    }
}
