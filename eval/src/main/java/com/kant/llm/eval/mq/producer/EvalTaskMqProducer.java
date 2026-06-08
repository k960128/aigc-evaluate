package com.kant.llm.eval.mq.producer;

import com.kant.llm.eval.mq.EvalMqTopics;
import com.kant.llm.eval.mq.message.EvalSampleExecutionMessage;
import com.kant.llm.eval.mq.message.EvalTaskSplitMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 评测任务 MQ 生产者。
 */
@Slf4j
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
        log.info("准备发送任务批次拆分 MQ 消息，topic: {}, taskDetailId: {}, taskId: {}, datasetId: {}, modelId: {}",
                EvalMqTopics.EVAL_TASK_SPLIT, message.getTaskDetailId(), message.getTaskId(),
                message.getDatasetId(), message.getModelId());
        try {
            rocketMQTemplate.convertAndSend(EvalMqTopics.EVAL_TASK_SPLIT, message);
            log.info("任务批次拆分 MQ 消息发送成功，topic: {}, taskDetailId: {}, taskId: {}",
                    EvalMqTopics.EVAL_TASK_SPLIT, message.getTaskDetailId(), message.getTaskId());
        } catch (Exception ex) {
            log.error("任务批次拆分 MQ 消息发送失败，topic: {}, taskDetailId: {}, taskId: {}",
                    EvalMqTopics.EVAL_TASK_SPLIT, message.getTaskDetailId(), message.getTaskId(), ex);
            throw ex;
        }
    }

    /**
     * 发送单样本执行消息。
     *
     * <p>当前阶段只完成投递，后续消费者会接入被测模型调用和三层漏斗判定。</p>
     */
    public void sendSampleExecutionMessage(EvalSampleExecutionMessage message) {
        log.info("准备发送单样本执行 MQ 消息，topic: {}, taskDetailId: {}, resultDetailId: {}, sampleId: {}, modelId: {}",
                EvalMqTopics.EXECUTION, message.getTaskDetailId(), message.getResultDetailId(),
                message.getSampleId(), message.getModelId());
        try {
            rocketMQTemplate.convertAndSend(EvalMqTopics.EXECUTION, message);
            log.info("单样本执行 MQ 消息发送成功，topic: {}, taskDetailId: {}, resultDetailId: {}",
                    EvalMqTopics.EXECUTION, message.getTaskDetailId(), message.getResultDetailId());
        } catch (Exception ex) {
            log.error("单样本执行 MQ 消息发送失败，topic: {}, taskDetailId: {}, resultDetailId: {}",
                    EvalMqTopics.EXECUTION, message.getTaskDetailId(), message.getResultDetailId(), ex);
            throw ex;
        }
    }
}
