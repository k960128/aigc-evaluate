package com.kant.llm.eval.mq.consumer;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.llm.eval.common.enums.EvalResultStatusEnums;
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
import java.util.Objects;

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
        log.info("开始消费任务批次拆分 MQ 消息，topic: {}, taskDetailId: {}, taskId: {}, datasetId: {}, modelId: {}",
                EvalMqTopics.EVAL_TASK_SPLIT, message.getTaskDetailId(), message.getTaskId(),
                message.getDatasetId(), message.getModelId());
        EvalTaskDetailDO taskDetail = evalTaskDetailMapper.selectById(message.getTaskDetailId());
        if (taskDetail == null) {
            log.warn("评测任务批次不存在，跳过拆分，消息: {}", message);
            return;
        }
        // MQ 至少一次投递，批次已经拆分后只补投递仍未处理的样本，防止 READY 后发送执行消息失败造成漏执行。
        if (TaskStatusEnums.READY.getCode().equals(taskDetail.getStatus())
                || TaskStatusEnums.RUNNING.getCode().equals(taskDetail.getStatus())) {
            resendPendingExecutionMessages(taskDetail);
            return;
        }
        if (TaskStatusEnums.COMPLETED.getCode().equals(taskDetail.getStatus())
                || TaskStatusEnums.ERROR.getCode().equals(taskDetail.getStatus())
                || TaskStatusEnums.STOPPED.getCode().equals(taskDetail.getStatus())) {
            log.info("评测任务批次已处于终态，跳过重复拆分，taskDetailId: {}", taskDetail.getId());
            return;
        }

        // 标记为初始化中，表示该批次已经被拆分消费者接手。
        taskDetail.setStatus(TaskStatusEnums.INITIALIZING.getCode());
        taskDetail.setStartTime(LocalDateTime.now());
        evalTaskDetailMapper.updateById(taskDetail);
        log.info("评测任务批次进入拆分初始化状态，taskDetailId: {}, datasetId: {}",
                taskDetail.getId(), taskDetail.getDatasetId());

        List<DataSetSampleDO> samples = dataSetSampleMapper.selectList(new LambdaQueryWrapper<>(DataSetSampleDO.class)
                .eq(DataSetSampleDO::getDatasetId, taskDetail.getDatasetId()));
        if (CollectionUtil.isEmpty(samples)) {
            markTaskDetailError(taskDetail);
            log.warn("评测任务批次数据集为空，taskDetailId: {}, datasetId: {}", taskDetail.getId(), taskDetail.getDatasetId());
            return;
        }
        log.info("评测任务批次加载样本完成，taskDetailId: {}, sampleCount: {}",
                taskDetail.getId(), samples.size());

        // 每个样本先生成一条结果明细。执行消息会在批次 READY 后统一投递，避免执行消费者过早消费。
        List<EvalResultDetailDO> resultDetails = samples.stream()
                .map(sample -> splitSample(taskDetail, sample))
                .filter(Objects::nonNull)
                .toList();

        // 用户可能在拆分过程中点击停止，置 READY 和投递执行消息前必须再次确认批次状态。
        EvalTaskDetailDO latestTaskDetail = evalTaskDetailMapper.selectById(taskDetail.getId());
        if (latestTaskDetail == null || TaskStatusEnums.STOPPED.getCode().equals(latestTaskDetail.getStatus())) {
            log.info("评测任务批次已在拆分过程中被终止，停止投递执行消息，taskDetailId: {}", taskDetail.getId());
            return;
        }

        taskDetail.setStatus(TaskStatusEnums.READY.getCode());
        taskDetail.setTotalCount(samples.size());
        evalTaskDetailMapper.updateById(taskDetail);
        log.info("评测任务批次拆分结果明细完成，taskDetailId: {}, resultDetailCount: {}",
                taskDetail.getId(), resultDetails.size());

        // 批次已进入 READY 后再投递单样本执行消息，保证后续消费者能稳定推进 RUNNING 状态。
        resultDetails.forEach(resultDetail -> sendSampleExecutionMessage(taskDetail, resultDetail));
        log.info("评测任务批次拆分完成，taskDetailId: {}, totalCount: {}", taskDetail.getId(), samples.size());
    }

    /**
     * 幂等生成单条样本结果明细。
     *
     * <p>该方法只负责拆分落库，不投递执行消息；执行消息在批次 READY 后统一发送。</p>
     */
    private EvalResultDetailDO splitSample(EvalTaskDetailDO taskDetail, DataSetSampleDO sample) {
        // 先查后插配合唯一索引，保证重复消费同一批次拆分消息时不会重复生成结果明细。
        EvalResultDetailDO resultDetail = findResultDetail(taskDetail.getId(), sample.getId());
        if (resultDetail == null) {
            resultDetail = EvalResultDetailDO.builder()
                    .id(IdUtil.getSnowflake().nextId())
                    .taskId(taskDetail.getTaskId())
                    .taskDetailId(taskDetail.getId())
                    .sampleId(sample.getId())
                    .inputText(sample.getQuestion())
                    .riskDetailsId(sample.getRiskDetailsId())
                    .status(EvalResultStatusEnums.PENDING.getCode())
                    .build();
            try {
                evalResultDetailMapper.insert(resultDetail);
                log.info("评测结果明细创建成功，taskDetailId: {}, resultDetailId: {}, sampleId: {}",
                        taskDetail.getId(), resultDetail.getId(), sample.getId());
            } catch (DuplicateKeyException ex) {
                // 并发消费或重试下可能已经被另一条执行流插入，回查即可继续投递执行消息。
                resultDetail = findResultDetail(taskDetail.getId(), sample.getId());
                log.info("评测结果明细已存在，复用已有明细，taskDetailId: {}, resultDetailId: {}, sampleId: {}",
                        taskDetail.getId(), resultDetail == null ? null : resultDetail.getId(), sample.getId());
            }
        }
        if (resultDetail == null) {
            throw new IllegalStateException("评测结果明细创建失败，taskDetailId=" + taskDetail.getId()
                    + ", sampleId=" + sample.getId());
        }
        return resultDetail;
    }

    /**
     * 将拆分后的结果明细投递到单样本执行 Topic。
     */
    private void sendSampleExecutionMessage(EvalTaskDetailDO taskDetail, EvalResultDetailDO resultDetail) {
        log.info("准备投递拆分后的单样本执行消息，taskDetailId: {}, resultDetailId: {}, sampleId: {}",
                taskDetail.getId(), resultDetail.getId(), resultDetail.getSampleId());
        evalTaskMqProducer.sendSampleExecutionMessage(EvalSampleExecutionMessage.builder()
                .taskDetailId(taskDetail.getId())
                .resultDetailId(resultDetail.getId())
                .taskId(taskDetail.getTaskId())
                .sampleId(resultDetail.getSampleId())
                .modelId(taskDetail.getModelId())
                .build());
    }

    /**
     * 拆分消息重试时补投递仍处于未处理状态的结果明细。
     *
     * <p>Execution 消费者本身有单结果锁和终态幂等校验，因此这里补发 PENDING 消息更安全：
     * 宁可让下游跳过重复消息，也不能因为一次发送异常导致样本永远不执行。</p>
     */
    private void resendPendingExecutionMessages(EvalTaskDetailDO taskDetail) {
        List<EvalResultDetailDO> pendingResultDetails = evalResultDetailMapper.selectList(new LambdaQueryWrapper<>(EvalResultDetailDO.class)
                .eq(EvalResultDetailDO::getTaskDetailId, taskDetail.getId())
                .eq(EvalResultDetailDO::getStatus, EvalResultStatusEnums.PENDING.getCode()));
        if (CollectionUtil.isEmpty(pendingResultDetails)) {
            log.info("评测任务批次无待补发样本，taskDetailId: {}", taskDetail.getId());
            return;
        }
        pendingResultDetails.forEach(resultDetail -> sendSampleExecutionMessage(taskDetail, resultDetail));
        log.info("评测任务批次补发待执行样本完成，taskDetailId: {}, pendingCount: {}",
                taskDetail.getId(), pendingResultDetails.size());
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
