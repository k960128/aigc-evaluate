package com.kant.llm.eval.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.llm.eval.common.enums.TaskStatusEnums;
import com.kant.llm.eval.common.errorcode.BaseErrorCode;
import com.kant.llm.eval.common.exception.ServiceException;
import com.kant.llm.eval.dao.entity.DataSetSampleDO;
import com.kant.llm.eval.dao.entity.EvalTaskDO;
import com.kant.llm.eval.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.dao.mapper.DataSetSampleMapper;
import com.kant.llm.eval.dao.mapper.EvalTaskDetailMapper;
import com.kant.llm.eval.dao.mapper.EvalTaskMapper;
import com.kant.llm.eval.dao.mapper.ModelInfoMapper;
import com.kant.llm.eval.dto.req.CreateEvalTaskRequest;
import com.kant.llm.eval.mq.message.EvalTaskSplitMessage;
import com.kant.llm.eval.mq.producer.EvalTaskMqProducer;
import com.kant.llm.eval.service.EvalTaskService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EvalTaskServiceImpl implements EvalTaskService {
    private static final String SUBMIT_LOCK_KEY_PREFIX = "aigc-eval:eval-task:submit:";

    /**
     * 同一个评测任务同一时间只允许存在一个活跃批次。
     *
     * <p>评测任务绑定了被测大模型，重复提交会导致同一模型链路下结果混乱。</p>
     */
    private static final List<Integer> ACTIVE_TASK_DETAIL_STATUS = List.of(
            TaskStatusEnums.CREATING.getCode(),
            TaskStatusEnums.INITIALIZING.getCode(),
            TaskStatusEnums.READY.getCode(),
            TaskStatusEnums.RUNNING.getCode()
    );

    private final EvalTaskMapper evalTaskMapper;
    private final ModelInfoMapper modelInfoMapper;
    private final DataSetSampleMapper dataSetSampleMapper;
    private final EvalTaskDetailMapper evalTaskDetailMapper;
    private final EvalTaskMqProducer evalTaskMqProducer;
    private final RedissonClient redissonClient;

    public EvalTaskServiceImpl(EvalTaskMapper evalTaskMapper,
                               ModelInfoMapper modelInfoMapper,
                               DataSetSampleMapper dataSetSampleMapper,
                               EvalTaskDetailMapper evalTaskDetailMapper,
                               EvalTaskMqProducer evalTaskMqProducer,
                               RedissonClient redissonClient) {
        this.evalTaskMapper = evalTaskMapper;
        this.modelInfoMapper = modelInfoMapper;
        this.dataSetSampleMapper = dataSetSampleMapper;
        this.evalTaskDetailMapper = evalTaskDetailMapper;
        this.evalTaskMqProducer = evalTaskMqProducer;
        this.redissonClient = redissonClient;
    }

    @Override
    public void createEvalTask(CreateEvalTaskRequest request) {
        log.info("创建评测任务: {}", request);
    }

    @Override
    public Boolean submitEvalTask(Long taskId) {
        RLock lock = redissonClient.getLock(SUBMIT_LOCK_KEY_PREFIX + taskId);
        boolean locked;
        try {
            locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("评测任务提交被中断", ex, BaseErrorCode.SERVICE_ERROR);
        }
        if (!locked) {
            throw new ServiceException("评测任务正在提交中，请勿重复提交");
        }
        try {
            // 1. 获取任务详情
            EvalTaskDO task = evalTaskMapper.selectById(taskId);
            if (task == null) {
                throw new ServiceException("评测任务不存在，无法启动");
            }
            // 2. 幂等校验：存在活跃批次时，说明该任务已经在提交、拆分或执行中。
            ensureNoActiveTaskDetail(taskId);
            // 3. 获取数据集
            Long sampleCount = dataSetSampleMapper.selectCount(new LambdaQueryWrapper<>(DataSetSampleDO.class)
                    .eq(DataSetSampleDO::getDatasetId, task.getDatasetId()));
            if (sampleCount == null || sampleCount <= 0) {
                throw new ServiceException("评测任务数据集为空，无法启动");
            }
            // 4. 创建一次运行批次。同一个 eval_task 可以多次提交，但同一时间只能存在一个活跃批次。
            Long taskDetailId = IdUtil.getSnowflake().nextId();
            Long serialNo = IdUtil.getSnowflake().nextId();
            EvalTaskDetailDO evalTaskDetailDO = EvalTaskDetailDO.builder()
                    .id(taskDetailId)
                    .taskId(taskId)
                    .serialNo(serialNo)
                    .taskName(task.getTaskName())
                    .modelId(task.getModelId())
                    .datasetId(task.getDatasetId())
                    .status(TaskStatusEnums.CREATING.getCode())
                    .totalCount(Math.toIntExact(sampleCount))
                    .finishedCount(0)
                    .failedCount(0)
                    .tokenUsage(0)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            evalTaskDetailMapper.insert(evalTaskDetailDO);
            try {
                // 5. 只发送批次拆分消息，样本枚举交给 MQ 消费者异步完成。
                evalTaskMqProducer.sendTaskSplitMessage(EvalTaskSplitMessage.builder()
                        .taskDetailId(taskDetailId)
                        .taskId(taskId)
                        .modelId(task.getModelId())
                        .datasetId(task.getDatasetId())
                        .serialNo(serialNo)
                        .build());
            } catch (Exception ex) {
                // 保留 ERROR 批次，方便后续排查是哪一次提交没有进入拆分链路。
                evalTaskDetailDO.setStatus(TaskStatusEnums.ERROR.getCode());
                evalTaskDetailMapper.updateById(evalTaskDetailDO);
                throw new ServiceException("评测任务拆分消息发送失败", ex, BaseErrorCode.SERVICE_ERROR);
            }
            return true;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void ensureNoActiveTaskDetail(Long taskId) {
        EvalTaskDetailDO activeTaskDetail = evalTaskDetailMapper.selectOne(new LambdaQueryWrapper<>(EvalTaskDetailDO.class)
                .eq(EvalTaskDetailDO::getTaskId, taskId)
                .in(EvalTaskDetailDO::getStatus, ACTIVE_TASK_DETAIL_STATUS)
                .orderByDesc(EvalTaskDetailDO::getCreateTime)
                .last("LIMIT 1"));
        if (activeTaskDetail != null) {
            throw new ServiceException("评测任务已在运行中，请勿重复提交");
        }
    }


    /**
     * 查询待处理任务
     */
    @Override
    public List<EvalTaskDetailDO> selectPendingTasks() {
        return evalTaskDetailMapper.selectList(new LambdaQueryWrapper<>(EvalTaskDetailDO.class)
                .eq(EvalTaskDetailDO::getStatus, TaskStatusEnums.CREATING.getCode()));
    }
}
