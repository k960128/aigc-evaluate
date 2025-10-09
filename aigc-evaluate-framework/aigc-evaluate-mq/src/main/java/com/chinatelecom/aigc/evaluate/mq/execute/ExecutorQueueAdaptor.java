package com.chinatelecom.aigc.evaluate.mq.execute;

import com.chinatelecom.aigc.evaluate.mq.param.MessageBody;

/**
 * 任务队列执行器
 * @author AIGC
 */
public interface ExecutorQueueAdaptor {
    /**
     * 推送步骤信息
     *
     * @param msgKey   the msgKey
     * @param messageBody   the messageBody
     */
    boolean send(String msgKey, MessageBody messageBody);

    /**
     * 消费步骤信息
     *
     * @param taskId the task id
     * @return the json object
     */
    MessageBody consumer(String taskId);

    /**
     * 获取任务队列适配器类型
     *
     * @return the type
     */
    String getType();
}
