package com.kant.framework.mq.producer;

import org.apache.rocketmq.client.producer.SendResult;

import java.util.function.Consumer;

/**
 * 消息队列生产者接口
 */
public interface MessageQueueProducer {

    /**
     * 发送消息
     * @return RocketMQ 发送结果，包含 msgId、sendStatus 等信息
     */
    SendResult send(MessageSendRequest messageSendRequest);

    /**
     * 发送事务消息
     * <p>
     * 流程：发送 half 消息 → 执行本地事务 → 根据结果 commit/rollback
     * <p>
     * 事务回查由按 topic 注册的 {@link TransactionChecker} 处理，需提前通过
     * {@link DelegatingTransactionListener#registerChecker(String, TransactionChecker)} 注册
     */
    void sendInTransaction(MessageSendRequest messageSendRequest);
}
