package com.chinatelecom.aigc.evaluate.mq.execute;
/**
 * AbstractExecutorQueue
 * @author AIGC
 */
public abstract class AbstractExecutorQueue implements ExecutorQueueAdaptor {
    public final static String EXECUTOR_QUEUE_KEY_PREFIX = "TASK_EXECUTOR_QUEUE#%s";


    /**
     * Gets queue key.
     *
     * @return the queue key
     */
    protected String getQueueKey() {
        return EXECUTOR_QUEUE_KEY_PREFIX;
    }
}
