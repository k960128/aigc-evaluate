package com.kant.llm.eval.mq.producer;

import com.kant.llm.eval.mq.EvalMqTopics;
import com.kant.llm.eval.mq.message.EvalSampleExecutionMessage;
import com.kant.llm.eval.mq.message.EvalTaskSplitMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 评测任务 MQ 生产者。
 */
@Component
public class EvalTaskMqProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public EvalTaskMqProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 发送任务批次拆分消息。
     *
     * <p>该消息只承载批次维度信息，真正的样本枚举在消费者侧完成，避免提交接口被大数据集拖慢。</p>
     */
    public void sendTaskSplitMessage(EvalTaskSplitMessage message) {
        rocketMQTemplate.convertAndSend(EvalMqTopics.EVAL_TASK_SPLIT, message);
    }

    /**
     * 发送单样本执行消息。
     *
     * <p>当前阶段只完成投递，后续消费者会接入被测模型调用和三层漏斗判定。</p>
     */
    public void sendSampleExecutionMessage(EvalSampleExecutionMessage message) {
        rocketMQTemplate.convertAndSend(EvalMqTopics.EXECUTION, message);
    }
}
