package com.chinatelecom.aigc.evaluate.mq.execute.local;

import com.chinatelecom.aigc.evaluate.mq.execute.AbstractExecutorQueue;
import com.chinatelecom.aigc.evaluate.mq.param.MessageBody;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class BlockingExecuteQueue extends AbstractExecutorQueue {
    /**
     * 任务的本地阻塞队列
     */
    private final Map<String, BlockingQueue<MessageBody>> queueMap = new ConcurrentHashMap<>();

    @Override
    public boolean send(String msgKey, MessageBody messageBody) {
        log.info("send (blocking) messageKey: {}, msgId: {}", msgKey, messageBody.getMsgId());
        try {
            getQueue(msgKey).put(messageBody); // 阻塞式添加
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("send interrupted: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public MessageBody consumer(String msgKey) {
        try {
            MessageBody messageBody = getQueue(msgKey).take(); // 阻塞式消费
            log.info("consumer (blocking) messageKey: {}, msgId: {}", msgKey, messageBody.getMsgId());
            return messageBody;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("consumer interrupted: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String getType() {
        return "blocking";
    }

    private BlockingQueue<MessageBody> getQueue(String msgKey) {
        String key = String.format(getQueueKey(), msgKey);
        return queueMap.computeIfAbsent(key, k -> new LinkedBlockingQueue<>());
    }
}
