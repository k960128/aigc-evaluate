package com.chinatelecom.aigc.evaluate.mq.execute;

import com.chinatelecom.aigc.evaluate.mq.execute.local.BlockingExecuteQueue;
import com.chinatelecom.aigc.evaluate.mq.execute.local.LocalExecuteQueue;
import com.chinatelecom.aigc.evaluate.mq.param.MessageBody;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ExecutorQueueService {

    private final Map<String, ExecutorQueueAdaptor> executorQueueAdaptorMap = new ConcurrentHashMap<>();

    // 新增队列类型标识
    private final String queueType;

    // 构造函数注入队列类型
    public ExecutorQueueService(String queueType) {
        this.queueType = queueType != null ? queueType : "blocking";
    }

    public boolean product(String msgKey, MessageBody messageBody) {
        return getExecutorQueueAdaptor().send(msgKey, messageBody);
    }

    public MessageBody consumer(String msgKey) {
        return getExecutorQueueAdaptor().consumer(msgKey);
    }

    private ExecutorQueueAdaptor getExecutorQueueAdaptor() {
        String key = queueType + "ExecutorQueue";
        return executorQueueAdaptorMap.computeIfAbsent(key, k -> {
            switch (queueType) {
                case "blocking":
                    return new BlockingExecuteQueue();
                case "local":
                default:
                    return new LocalExecuteQueue();
            }
        });
    }
}
