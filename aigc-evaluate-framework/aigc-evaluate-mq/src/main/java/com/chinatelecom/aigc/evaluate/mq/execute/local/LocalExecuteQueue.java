package com.chinatelecom.aigc.evaluate.mq.execute.local;

import com.alibaba.fastjson.JSON;
import com.chinatelecom.aigc.evaluate.mq.execute.AbstractExecutorQueue;
import com.chinatelecom.aigc.evaluate.mq.param.MessageBody;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
public class LocalExecuteQueue extends AbstractExecutorQueue {
    /**
     * 任务的本地队列
     */
    private final Map<String, ConcurrentLinkedQueue<MessageBody>> queueMap = new ConcurrentHashMap<>();

    @Override
    public boolean send(String msgKey, MessageBody messageBody) {
        log.info("send messageKey : {} ,msgId {}", msgKey, messageBody.getMsgId());
        return getQueue(msgKey).add(messageBody);
    }

    @Override
    public MessageBody consumer(String msgKey) {
        MessageBody messageBody = getQueue(msgKey).poll();
        log.info("consumer messageKey : {} ,msgId {}", msgKey, messageBody.getMsgId());
        return messageBody;
    }

    @Override
    public String getType() {
        return "local";
    }


    private ConcurrentLinkedQueue<MessageBody> getQueue(String msgKey) {
        String key = String.format(getQueueKey(), msgKey);
        ConcurrentLinkedQueue<MessageBody> concurrentLinkedQueue = queueMap.get(key);
        if (null == concurrentLinkedQueue) {
            concurrentLinkedQueue = new ConcurrentLinkedQueue<>();
        }
        queueMap.put(key, concurrentLinkedQueue);
        return concurrentLinkedQueue;
    }
}
