package com.kant.llm.eval.mq.consumer;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.llm.eval.common.enums.TaskStatusEnums;
import com.kant.llm.eval.dao.entity.DataSetSampleDO;
import com.kant.llm.eval.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.dao.mapper.DataSetSampleMapper;
import com.kant.llm.eval.dao.mapper.EvalResultDetailMapper;
import com.kant.llm.eval.dao.mapper.EvalTaskDetailMapper;
import com.kant.llm.eval.mq.EvalMqTopics;
import com.kant.llm.eval.mq.message.EvalSampleExecutionMessage;
import com.kant.llm.eval.mq.message.EvalTaskSplitMessage;
import com.kant.llm.eval.mq.producer.EvalTaskMqProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评测任务批次拆分消费者。
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = EvalMqTopics.EVAL_TASK_SPLIT,
        consumerGroup = "aigc-eval-task-split-consumer-group"
)
public class EvalTaskSplitConsumer implements RocketMQListener<EvalTaskSplitMessage> {

    private final EvalTaskDetailMapper evalTaskDetailMapper;
    private final EvalResultDetailMapper evalResultDetailMapper;
    private final DataSetSampleMapper dataSetSampleMapper;
    private final EvalTaskMqProducer evalTaskMqProducer;

    public EvalTaskSplitConsumer(EvalTaskDetailMapper evalTaskDetailMapper,
                                 EvalResultDetailMapper evalResultDetailMapper,
                                 DataSetSampleMapper dataSetSampleMapper,
                                 EvalTaskMqProducer evalTaskMqProducer) {
        this.evalTaskDetailMapper = evalTaskDetailMapper;
        this.evalResultDetailMapper = evalResultDetailMapper;
        this.dataSetSampleMapper = dataSetSampleMapper;
        this.evalTaskMqProducer = evalTaskMqProducer;
    }

    @Override
    public void onMessage(EvalTaskSplitMessage message) {
        EvalTaskDetailDO taskDetail = evalTaskDetailMapper.selectById(message.getTaskDetailId());
        if (taskDetail == null) {
            log.warn("评测任务批次不存在，跳过拆分，消息: {}", message);
            return;
        }
        // MQ 至少一次投递，批次已经完成拆分或进入执行阶段时直接跳过，避免重复投递执行消息。
        if (TaskStatusEnums.READY.getCode().equals(taskDetail.getStatus())
                || TaskStatusEnums.RUNNING.getCode().equals(taskDetail.getStatus())
                || TaskStatusEnums.COMPLETED.getCode().equals(taskDetail.getStatus())) {
            log.info("评测任务批次已完成拆分或已进入执行阶段，跳过重复拆分，taskDetailId: {}", taskDetail.getId());
            return;
        }

        // 标记为初始化中，表示该批次已经被拆分消费者接手。
        taskDetail.setStatus(TaskStatusEnums.INITIALIZING.getCode());
        taskDetail.setStartTime(LocalDateTime.now());
        evalTaskDetailMapper.updateById(taskDetail);

        List<DataSetSampleDO> samples = dataSetSampleMapper.selectList(new LambdaQueryWrapper<>(DataSetSampleDO.class)
                .eq(DataSetSampleDO::getDatasetId, taskDetail.getDatasetId()));
        if (CollectionUtil.isEmpty(samples)) {
            markTaskDetailError(taskDetail);
            log.warn("评测任务批次数据集为空，taskDetailId: {}, datasetId: {}", taskDetail.getId(), taskDetail.getDatasetId());
            return;
        }

        // 每个样本生成一条结果明细，并继续投递到单样本执行 Topic。
        samples.forEach(sample -> splitSample(taskDetail, sample));

        taskDetail.setStatus(TaskStatusEnums.READY.getCode());
        taskDetail.setTotalCount(samples.size());
        evalTaskDetailMapper.updateById(taskDetail);
        log.info("评测任务批次拆分完成，taskDetailId: {}, totalCount: {}", taskDetail.getId(), samples.size());
    }

    private void splitSample(EvalTaskDetailDO taskDetail, DataSetSampleDO sample) {
        // 先查后插配合唯一索引，保证重复消费同一批次拆分消息时不会重复生成结果明细。
        EvalResultDetailDO resultDetail = findResultDetail(taskDetail.getId(), sample.getId());
        if (resultDetail == null) {
            resultDetail = EvalResultDetailDO.builder()
                    .id(IdUtil.getSnowflake().nextId())
                    .taskId(taskDetail.getTaskId())
                    .taskDetailId(taskDetail.getId())
                    .sampleId(sample.getId())
                    .inputText(sample.getInputText())
                    .status(0)
                    .build();
            try {
                evalResultDetailMapper.insert(resultDetail);
            } catch (DuplicateKeyException ex) {
                // 并发消费或重试下可能已经被另一条执行流插入，回查即可继续投递执行消息。
                resultDetail = findResultDetail(taskDetail.getId(), sample.getId());
            }
        }
        if (resultDetail == null) {
            throw new IllegalStateException("评测结果明细创建失败，taskDetailId=" + taskDetail.getId()
                    + ", sampleId=" + sample.getId());
        }
        evalTaskMqProducer.sendSampleExecutionMessage(EvalSampleExecutionMessage.builder()
                .taskDetailId(taskDetail.getId())
                .resultDetailId(resultDetail.getId())
                .taskId(taskDetail.getTaskId())
                .sampleId(sample.getId())
                .modelId(taskDetail.getModelId())
                .build());
    }

    /**
     * 根据批次和样本查询结果明细。
     *
     * <p>taskDetailId 是批次隔离字段，防止同一个 eval_task 多次提交后结果混在一起。</p>
     */
    private EvalResultDetailDO findResultDetail(Long taskDetailId, Long sampleId) {
        return evalResultDetailMapper.selectOne(new LambdaQueryWrapper<>(EvalResultDetailDO.class)
                .eq(EvalResultDetailDO::getTaskDetailId, taskDetailId)
                .eq(EvalResultDetailDO::getSampleId, sampleId)
                .last("LIMIT 1"));
    }

    /**
     * 拆分前置条件异常时标记批次失败。
     */
    private void markTaskDetailError(EvalTaskDetailDO taskDetail) {
        taskDetail.setStatus(TaskStatusEnums.ERROR.getCode());
        taskDetail.setEndTime(LocalDateTime.now());
        evalTaskDetailMapper.updateById(taskDetail);
    }
}
