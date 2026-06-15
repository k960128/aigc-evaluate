package com.kant.llm.eval.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.fastjson2.JSON;
import com.kant.llm.eval.common.enums.EvalResultStatusEnums;
import com.kant.llm.eval.common.enums.PipelineNodeCodeEnums;
import com.kant.llm.eval.common.enums.PipelineNodeStatusEnums;
import com.kant.llm.eval.common.enums.TaskStatusEnums;
import com.kant.llm.eval.common.errorcode.BaseErrorCode;
import com.kant.llm.eval.common.exception.ServiceException;
import com.kant.llm.eval.dao.entity.DataSetDO;
import com.kant.llm.eval.dao.entity.DataSetSampleDO;
import com.kant.llm.eval.dao.entity.EvalPipelineNodeDetailDO;
import com.kant.llm.eval.dao.entity.EvalResultDetailDO;
import com.kant.llm.eval.dao.entity.EvalTaskDO;
import com.kant.llm.eval.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.dao.entity.ModelInfoDO;
import com.kant.llm.eval.dao.mapper.DataSetMapper;
import com.kant.llm.eval.dao.mapper.DataSetSampleMapper;
import com.kant.llm.eval.dao.mapper.EvalPipelineNodeDetailMapper;
import com.kant.llm.eval.dao.mapper.EvalResultDetailMapper;
import com.kant.llm.eval.dao.mapper.EvalTaskDetailMapper;
import com.kant.llm.eval.dao.mapper.EvalTaskMapper;
import com.kant.llm.eval.dao.mapper.ModelInfoMapper;
import com.kant.llm.eval.dto.req.CreateEvalTaskRequest;
import com.kant.llm.eval.dto.req.EvalResultDetailPageRequest;
import com.kant.llm.eval.dto.req.EvalTaskDetailPageRequest;
import com.kant.llm.eval.dto.req.EvalTaskPageRequest;
import com.kant.llm.eval.dto.resp.EvalPipelineNodeDetailVO;
import com.kant.llm.eval.dto.resp.EvalResultDetailVO;
import com.kant.llm.eval.dto.resp.EvalTaskDetailVO;
import com.kant.llm.eval.dto.resp.EvalTaskStatusVO;
import com.kant.llm.eval.dto.resp.EvalTaskVO;
import com.kant.llm.eval.mq.message.EvalTaskSplitMessage;
import com.kant.llm.eval.mq.producer.EvalTaskMqProducer;
import com.kant.llm.eval.service.EvalTaskService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EvalTaskServiceImpl implements EvalTaskService {
    private static final String TASK_OPERATION_LOCK_KEY_PREFIX = "aigc-eval:eval-task:operation:";

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
    private final DataSetMapper dataSetMapper;
    private final DataSetSampleMapper dataSetSampleMapper;
    private final EvalPipelineNodeDetailMapper evalPipelineNodeDetailMapper;
    private final EvalResultDetailMapper evalResultDetailMapper;
    private final EvalTaskDetailMapper evalTaskDetailMapper;
    private final EvalTaskMqProducer evalTaskMqProducer;
    private final RedissonClient redissonClient;

    public EvalTaskServiceImpl(EvalTaskMapper evalTaskMapper,
                               ModelInfoMapper modelInfoMapper,
                               DataSetMapper dataSetMapper,
                               DataSetSampleMapper dataSetSampleMapper,
                               EvalPipelineNodeDetailMapper evalPipelineNodeDetailMapper,
                               EvalResultDetailMapper evalResultDetailMapper,
                               EvalTaskDetailMapper evalTaskDetailMapper,
                               EvalTaskMqProducer evalTaskMqProducer,
                               RedissonClient redissonClient) {
        this.evalTaskMapper = evalTaskMapper;
        this.modelInfoMapper = modelInfoMapper;
        this.dataSetMapper = dataSetMapper;
        this.dataSetSampleMapper = dataSetSampleMapper;
        this.evalPipelineNodeDetailMapper = evalPipelineNodeDetailMapper;
        this.evalResultDetailMapper = evalResultDetailMapper;
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
        RLock lock = redissonClient.getLock(TASK_OPERATION_LOCK_KEY_PREFIX + taskId);
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

    @Override
    public Boolean stopEvalTask(Long taskId) {
        RLock lock = redissonClient.getLock(TASK_OPERATION_LOCK_KEY_PREFIX + taskId);
        boolean locked;
        try {
            locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("评测任务停止被中断", ex, BaseErrorCode.SERVICE_ERROR);
        }
        if (!locked) {
            throw new ServiceException("评测任务正在停止中，请勿重复操作");
        }
        try {
            // 终止语义只针对当前活跃批次，历史已完成批次和已完成样本结果全部保留。
            EvalTaskDetailDO activeTaskDetail = findActiveTaskDetail(taskId);
            if (activeTaskDetail == null) {
                log.info("评测任务不存在活跃批次，无需停止，taskId: {}", taskId);
                return true;
            }
            int stoppedRows = evalTaskDetailMapper.update(null, new LambdaUpdateWrapper<EvalTaskDetailDO>()
                    .eq(EvalTaskDetailDO::getId, activeTaskDetail.getId())
                    .in(EvalTaskDetailDO::getStatus, ACTIVE_TASK_DETAIL_STATUS)
                    .set(EvalTaskDetailDO::getStatus, TaskStatusEnums.STOPPED.getCode())
                    .set(EvalTaskDetailDO::getEndTime, LocalDateTime.now()));
            if (stoppedRows <= 0) {
                log.info("评测任务批次状态已变化，停止请求无需重复处理，taskDetailId: {}", activeTaskDetail.getId());
                return true;
            }
            markPendingResultStopped(activeTaskDetail.getId());
            log.info("评测任务批次已终止，taskId: {}, taskDetailId: {}", taskId, activeTaskDetail.getId());
            return true;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 分页查询评测任务定义列表，并补充最近一次执行批次的进度摘要。
     */
    @Override
    public Page<EvalTaskVO> pageEvalTask(EvalTaskPageRequest request) {
        EvalTaskPageRequest queryRequest = request == null ? new EvalTaskPageRequest() : request;
        LambdaQueryWrapper<EvalTaskDO> queryWrapper = new LambdaQueryWrapper<>();
        if (queryRequest.getTaskName() != null && !queryRequest.getTaskName().isBlank()) {
            queryWrapper.like(EvalTaskDO::getTaskName, queryRequest.getTaskName().trim());
        }
        if (queryRequest.getModelId() != null) {
            queryWrapper.eq(EvalTaskDO::getModelId, queryRequest.getModelId());
        }
        if (queryRequest.getDatasetId() != null) {
            queryWrapper.eq(EvalTaskDO::getDatasetId, queryRequest.getDatasetId());
        }
        queryWrapper.orderByDesc(EvalTaskDO::getCreateTime);

        Page<EvalTaskDO> pageResult = evalTaskMapper.selectPage(new Page<>(pageCurrent(queryRequest.getCurrent()),
                pageSize(queryRequest.getSize())), queryWrapper);
        List<EvalTaskDO> records = pageResult.getRecords();
        Map<Long, ModelInfoDO> modelInfoMap = buildModelInfoMap(records.stream()
                .map(EvalTaskDO::getModelId)
                .collect(Collectors.toSet()));
        Map<Long, DataSetDO> dataSetMap = buildDataSetMap(records.stream()
                .map(EvalTaskDO::getDatasetId)
                .collect(Collectors.toSet()));
        Map<Long, EvalTaskDetailDO> latestTaskDetailMap = buildLatestTaskDetailMap(records.stream()
                .map(EvalTaskDO::getId)
                .collect(Collectors.toList()));

        Page<EvalTaskVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(records.stream()
                .map(task -> convertToTaskVO(task, modelInfoMap, dataSetMap, latestTaskDetailMap.get(task.getId())))
                .collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 分页查询指定评测任务的执行批次列表。
     */
    @Override
    public Page<EvalTaskDetailVO> pageEvalTaskDetail(EvalTaskDetailPageRequest request) {
        if (request == null || request.getTaskId() == null) {
            throw new ServiceException("评测任务ID不能为空");
        }
        EvalTaskDO task = evalTaskMapper.selectById(request.getTaskId());
        if (task == null) {
            throw new ServiceException("评测任务不存在，无法查询执行批次");
        }
        LambdaQueryWrapper<EvalTaskDetailDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(EvalTaskDetailDO::getTaskId, request.getTaskId());
        if (request.getStatus() != null) {
            queryWrapper.eq(EvalTaskDetailDO::getStatus, request.getStatus());
        }
        queryWrapper.orderByDesc(EvalTaskDetailDO::getCreateTime);

        Page<EvalTaskDetailDO> pageResult = evalTaskDetailMapper.selectPage(new Page<>(pageCurrent(request.getCurrent()),
                pageSize(request.getSize())), queryWrapper);
        List<EvalTaskDetailDO> records = pageResult.getRecords();
        Map<Long, ModelInfoDO> modelInfoMap = buildModelInfoMap(records.stream()
                .map(EvalTaskDetailDO::getModelId)
                .collect(Collectors.toSet()));
        Map<Long, DataSetDO> dataSetMap = buildDataSetMap(records.stream()
                .map(EvalTaskDetailDO::getDatasetId)
                .collect(Collectors.toSet()));

        Page<EvalTaskDetailVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(records.stream()
                .map(taskDetail -> convertToTaskDetailVO(taskDetail, modelInfoMap, dataSetMap))
                .collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 分页查询评测结果明细列表，并补充样本参考信息和流水线日志标记。
     */
    @Override
    public Page<EvalResultDetailVO> pageEvalResultDetail(EvalResultDetailPageRequest request) {
        EvalResultDetailPageRequest queryRequest = request == null ? new EvalResultDetailPageRequest() : request;
        LambdaQueryWrapper<EvalResultDetailDO> queryWrapper = new LambdaQueryWrapper<>();
        if (queryRequest.getTaskDetailId() != null) {
            queryWrapper.eq(EvalResultDetailDO::getTaskDetailId, queryRequest.getTaskDetailId());
        }
        if (queryRequest.getTaskId() != null) {
            queryWrapper.eq(EvalResultDetailDO::getTaskId, queryRequest.getTaskId());
        }
        if (queryRequest.getSampleId() != null) {
            queryWrapper.eq(EvalResultDetailDO::getSampleId, queryRequest.getSampleId());
        }
        if (queryRequest.getStatus() != null) {
            queryWrapper.eq(EvalResultDetailDO::getStatus, queryRequest.getStatus());
        }
        if (queryRequest.getIsSafe() != null) {
            queryWrapper.eq(EvalResultDetailDO::getIsSafe, queryRequest.getIsSafe());
        }
        if (queryRequest.getKeyword() != null && !queryRequest.getKeyword().isBlank()) {
            queryWrapper.like(EvalResultDetailDO::getInputText, queryRequest.getKeyword().trim());
        }
        queryWrapper.orderByDesc(EvalResultDetailDO::getCreateTime)
                .orderByDesc(EvalResultDetailDO::getId);

        Page<EvalResultDetailDO> pageResult = evalResultDetailMapper.selectPage(new Page<>(pageCurrent(queryRequest.getCurrent()),
                pageSize(queryRequest.getSize())), queryWrapper);
        List<EvalResultDetailDO> records = pageResult.getRecords();
        Map<Long, DataSetSampleDO> sampleMap = buildDataSetSampleMap(records.stream()
                .map(EvalResultDetailDO::getSampleId)
                .collect(Collectors.toSet()));
        Set<Long> resultDetailIdsWithPipelineLog = buildResultDetailIdsWithPipelineLog(records.stream()
                .map(EvalResultDetailDO::getId)
                .collect(Collectors.toSet()));

        Page<EvalResultDetailVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(records.stream()
                .map(resultDetail -> convertToResultDetailVO(resultDetail, sampleMap.get(resultDetail.getSampleId()),
                        resultDetailIdsWithPipelineLog.contains(resultDetail.getId())))
                .collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 查询评测任务状态，优先返回活跃批次，没有活跃批次时返回最近一次批次。
     */
    @Override
    public EvalTaskStatusVO getEvalTaskStatus(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("评测任务ID不能为空");
        }
        EvalTaskDO task = evalTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("评测任务不存在，无法查询状态");
        }
        EvalTaskDetailDO activeTaskDetail = findActiveTaskDetail(taskId);
        boolean hasActiveBatch = activeTaskDetail != null;
        EvalTaskDetailDO taskDetail = hasActiveBatch ? activeTaskDetail : findLatestTaskDetail(taskId);
        if (taskDetail == null) {
            return EvalTaskStatusVO.builder()
                    .taskId(task.getId())
                    .taskName(task.getTaskName())
                    .hasTaskDetail(false)
                    .hasActiveBatch(false)
                    .statusDesc("未提交")
                    .progressPercent(0)
                    .build();
        }
        return convertToTaskStatusVO(task, taskDetail, hasActiveBatch);
    }

    /**
     * 查询单条样本结果对应的流水线节点日志。
     */
    @Override
    public List<EvalPipelineNodeDetailVO> listPipelineNodeLogs(Long resultDetailId) {
        if (resultDetailId == null) {
            throw new ServiceException("评测结果明细ID不能为空");
        }
        EvalResultDetailDO resultDetail = evalResultDetailMapper.selectById(resultDetailId);
        if (resultDetail == null) {
            throw new ServiceException("评测结果明细不存在，无法查询流水线日志");
        }
        List<EvalPipelineNodeDetailDO> nodeDetails = evalPipelineNodeDetailMapper.selectList(new LambdaQueryWrapper<EvalPipelineNodeDetailDO>()
                .eq(EvalPipelineNodeDetailDO::getResultDetailId, resultDetailId)
                .orderByAsc(EvalPipelineNodeDetailDO::getStartTime)
                .orderByAsc(EvalPipelineNodeDetailDO::getCreateTime)
                .orderByAsc(EvalPipelineNodeDetailDO::getId));
        return nodeDetails.stream()
                .map(this::convertToPipelineNodeDetailVO)
                .collect(Collectors.toList());
    }

    private void ensureNoActiveTaskDetail(Long taskId) {
        EvalTaskDetailDO activeTaskDetail = findActiveTaskDetail(taskId);
        if (activeTaskDetail != null) {
            throw new ServiceException("评测任务已在运行中，请勿重复提交");
        }
    }

    /**
     * 查询同一个评测任务当前仍在运行链路中的批次。
     *
     * <p>活跃状态包括提交、拆分、就绪和执行中；STOPPED/COMPLETED/ERROR 都属于终态。</p>
     */
    private EvalTaskDetailDO findActiveTaskDetail(Long taskId) {
        EvalTaskDetailDO activeTaskDetail = evalTaskDetailMapper.selectOne(new LambdaQueryWrapper<>(EvalTaskDetailDO.class)
                .eq(EvalTaskDetailDO::getTaskId, taskId)
                .in(EvalTaskDetailDO::getStatus, ACTIVE_TASK_DETAIL_STATUS)
                .orderByDesc(EvalTaskDetailDO::getCreateTime)
                .orderByDesc(EvalTaskDetailDO::getId)
                .last("LIMIT 1"));
        return activeTaskDetail;
    }

    /**
     * 查询任务最近一次执行批次，用于没有活跃批次时展示最终态。
     */
    private EvalTaskDetailDO findLatestTaskDetail(Long taskId) {
        return evalTaskDetailMapper.selectOne(new LambdaQueryWrapper<>(EvalTaskDetailDO.class)
                .eq(EvalTaskDetailDO::getTaskId, taskId)
                .orderByDesc(EvalTaskDetailDO::getCreateTime)
                .orderByDesc(EvalTaskDetailDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 将尚未进入模型调用的结果明细标记为已终止。
     *
     * <p>只更新 PENDING 状态，已自动评分、失败、人工核验的结果保持不变，确保用户可以查看停止前的真实结果。</p>
     */
    private void markPendingResultStopped(Long taskDetailId) {
        evalResultDetailMapper.update(null, new LambdaUpdateWrapper<EvalResultDetailDO>()
                .eq(EvalResultDetailDO::getTaskDetailId, taskDetailId)
                .eq(EvalResultDetailDO::getStatus, EvalResultStatusEnums.PENDING.getCode())
                .set(EvalResultDetailDO::getStatus, EvalResultStatusEnums.STOPPED.getCode())
                .set(EvalResultDetailDO::getErrorMsg, "用户主动终止评测，样本未执行"));
    }

    /**
     * 批量构建模型信息映射，避免任务分页时逐条查询模型名称。
     */
    private Map<Long, ModelInfoDO> buildModelInfoMap(Set<Long> modelIds) {
        Set<Long> filteredIds = filterNonNullIds(modelIds);
        if (filteredIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return modelInfoMapper.selectBatchIds(filteredIds).stream()
                .collect(Collectors.toMap(ModelInfoDO::getId, Function.identity(), (left, right) -> left));
    }

    /**
     * 批量构建数据集信息映射，避免任务分页时逐条查询数据集名称。
     */
    private Map<Long, DataSetDO> buildDataSetMap(Set<Long> datasetIds) {
        Set<Long> filteredIds = filterNonNullIds(datasetIds);
        if (filteredIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return dataSetMapper.selectBatchIds(filteredIds).stream()
                .collect(Collectors.toMap(DataSetDO::getId, Function.identity(), (left, right) -> left));
    }

    /**
     * 批量构建数据集样本映射，用于评测结果列表补充标准答案和评分规则。
     */
    private Map<Long, DataSetSampleDO> buildDataSetSampleMap(Set<Long> sampleIds) {
        Set<Long> filteredIds = filterNonNullIds(sampleIds);
        if (filteredIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return dataSetSampleMapper.selectBatchIds(filteredIds).stream()
                .collect(Collectors.toMap(DataSetSampleDO::getId, Function.identity(), (left, right) -> left));
    }

    /**
     * 批量查询存在流水线节点日志的结果明细 ID。
     */
    private Set<Long> buildResultDetailIdsWithPipelineLog(Set<Long> resultDetailIds) {
        Set<Long> filteredIds = filterNonNullIds(resultDetailIds);
        if (filteredIds.isEmpty()) {
            return Collections.emptySet();
        }
        return evalPipelineNodeDetailMapper.selectList(new LambdaQueryWrapper<EvalPipelineNodeDetailDO>()
                        .select(EvalPipelineNodeDetailDO::getResultDetailId)
                        .in(EvalPipelineNodeDetailDO::getResultDetailId, filteredIds))
                .stream()
                .map(EvalPipelineNodeDetailDO::getResultDetailId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 批量查询每个任务最近一次执行批次。
     */
    private Map<Long, EvalTaskDetailDO> buildLatestTaskDetailMap(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> filteredIds = taskIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (filteredIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EvalTaskDetailDO> taskDetails = evalTaskDetailMapper.selectList(new LambdaQueryWrapper<EvalTaskDetailDO>()
                .in(EvalTaskDetailDO::getTaskId, filteredIds)
                .orderByDesc(EvalTaskDetailDO::getCreateTime)
                .orderByDesc(EvalTaskDetailDO::getId));
        return taskDetails.stream()
                .collect(Collectors.toMap(EvalTaskDetailDO::getTaskId, Function.identity(), (latest, ignored) -> latest));
    }

    /**
     * 将任务定义实体转换为任务列表响应对象。
     */
    private EvalTaskVO convertToTaskVO(EvalTaskDO task,
                                       Map<Long, ModelInfoDO> modelInfoMap,
                                       Map<Long, DataSetDO> dataSetMap,
                                       EvalTaskDetailDO latestTaskDetail) {
        ModelInfoDO modelInfo = modelInfoMap.get(task.getModelId());
        DataSetDO dataSet = dataSetMap.get(task.getDatasetId());
        return EvalTaskVO.builder()
                .id(task.getId())
                .taskName(task.getTaskName())
                .modelId(task.getModelId())
                .modelName(modelInfo == null ? null : modelInfo.getModel())
                .datasetId(task.getDatasetId())
                .datasetName(dataSet == null ? null : dataSet.getDatasetName())
                .latestTaskDetailId(latestTaskDetail == null ? null : latestTaskDetail.getId())
                .latestStatus(latestTaskDetail == null ? null : latestTaskDetail.getStatus())
                .latestStatusDesc(latestTaskDetail == null ? "未提交" : taskStatusDesc(latestTaskDetail.getStatus()))
                .totalCount(latestTaskDetail == null ? null : latestTaskDetail.getTotalCount())
                .finishedCount(latestTaskDetail == null ? null : latestTaskDetail.getFinishedCount())
                .failedCount(latestTaskDetail == null ? null : latestTaskDetail.getFailedCount())
                .progressPercent(latestTaskDetail == null ? 0 : calculateProgressPercent(latestTaskDetail.getFinishedCount(), latestTaskDetail.getTotalCount()))
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .build();
    }

    /**
     * 将执行批次实体转换为执行批次列表响应对象。
     */
    private EvalTaskDetailVO convertToTaskDetailVO(EvalTaskDetailDO taskDetail,
                                                   Map<Long, ModelInfoDO> modelInfoMap,
                                                   Map<Long, DataSetDO> dataSetMap) {
        ModelInfoDO modelInfo = modelInfoMap.get(taskDetail.getModelId());
        DataSetDO dataSet = dataSetMap.get(taskDetail.getDatasetId());
        return EvalTaskDetailVO.builder()
                .id(taskDetail.getId())
                .taskId(taskDetail.getTaskId())
                .serialNo(taskDetail.getSerialNo())
                .taskName(taskDetail.getTaskName())
                .modelId(taskDetail.getModelId())
                .modelName(modelInfo == null ? null : modelInfo.getModel())
                .datasetId(taskDetail.getDatasetId())
                .datasetName(dataSet == null ? null : dataSet.getDatasetName())
                .status(taskDetail.getStatus())
                .statusDesc(taskStatusDesc(taskDetail.getStatus()))
                .totalCount(taskDetail.getTotalCount())
                .finishedCount(taskDetail.getFinishedCount())
                .failedCount(taskDetail.getFailedCount())
                .progressPercent(calculateProgressPercent(taskDetail.getFinishedCount(), taskDetail.getTotalCount()))
                .tokenUsage(taskDetail.getTokenUsage())
                .createTime(taskDetail.getCreateTime())
                .updateTime(taskDetail.getUpdateTime())
                .startTime(taskDetail.getStartTime())
                .endTime(taskDetail.getEndTime())
                .build();
    }

    /**
     * 将评测结果明细实体转换为分页响应对象。
     */
    private EvalResultDetailVO convertToResultDetailVO(EvalResultDetailDO resultDetail,
                                                       DataSetSampleDO sample,
                                                       boolean hasPipelineLog) {
        return EvalResultDetailVO.builder()
                .id(resultDetail.getId())
                .taskId(resultDetail.getTaskId())
                .taskDetailId(resultDetail.getTaskDetailId())
                .sampleId(resultDetail.getSampleId())
                .datasetId(sample == null ? null : sample.getDatasetId())
                .riskDetailsId(resultDetail.getRiskDetailsId())
                .inputText(resultDetail.getInputText())
                .modelOutput(resultDetail.getModelOutput())
                .rawResponse(resultDetail.getRawResponse())
                .latency(resultDetail.getLatency())
                .isSafe(resultDetail.getIsSafe())
                .status(resultDetail.getStatus())
                .statusDesc(evalResultStatusDesc(resultDetail.getStatus()))
                .errorMsg(resultDetail.getErrorMsg())
                .hasPipelineLog(hasPipelineLog)
                .createTime(resultDetail.getCreateTime())
                .updateTime(resultDetail.getUpdateTime())
                .build();
    }

    /**
     * 将任务和执行批次转换为任务状态响应对象。
     */
    private EvalTaskStatusVO convertToTaskStatusVO(EvalTaskDO task, EvalTaskDetailDO taskDetail, boolean hasActiveBatch) {
        return EvalTaskStatusVO.builder()
                .taskId(task.getId())
                .taskName(task.getTaskName())
                .hasTaskDetail(true)
                .hasActiveBatch(hasActiveBatch)
                .taskDetailId(taskDetail.getId())
                .serialNo(taskDetail.getSerialNo())
                .status(taskDetail.getStatus())
                .statusDesc(taskStatusDesc(taskDetail.getStatus()))
                .totalCount(taskDetail.getTotalCount())
                .finishedCount(taskDetail.getFinishedCount())
                .failedCount(taskDetail.getFailedCount())
                .progressPercent(calculateProgressPercent(taskDetail.getFinishedCount(), taskDetail.getTotalCount()))
                .startTime(taskDetail.getStartTime())
                .endTime(taskDetail.getEndTime())
                .createTime(taskDetail.getCreateTime())
                .updateTime(taskDetail.getUpdateTime())
                .build();
    }

    /**
     * 将流水线节点实体转换为接口响应对象。
     */
    private EvalPipelineNodeDetailVO convertToPipelineNodeDetailVO(EvalPipelineNodeDetailDO nodeDetail) {
        return EvalPipelineNodeDetailVO.builder()
                .id(nodeDetail.getId())
                .taskId(nodeDetail.getTaskId())
                .taskDetailId(nodeDetail.getTaskDetailId())
                .resultDetailId(nodeDetail.getResultDetailId())
                .sampleId(nodeDetail.getSampleId())
                .nodeCode(nodeDetail.getNodeCode())
                .nodeCodeDesc(pipelineNodeCodeDesc(nodeDetail.getNodeCode()))
                .status(nodeDetail.getStatus())
                .statusDesc(pipelineNodeStatusDesc(nodeDetail.getStatus()))
                .startTime(nodeDetail.getStartTime())
                .endTime(nodeDetail.getEndTime())
                .latency(nodeDetail.getLatency())
                .errorMsg(nodeDetail.getErrorMsg())
                .inputSnapshot(parseJsonSnapshot(nodeDetail.getInputSnapshot()))
                .outputSnapshot(parseJsonSnapshot(nodeDetail.getOutputSnapshot()))
                .nodeResult(parseJsonSnapshot(nodeDetail.getNodeResult()))
                .createTime(nodeDetail.getCreateTime())
                .updateTime(nodeDetail.getUpdateTime())
                .build();
    }

    /**
     * 计算批次进度百分比，样本总数为空或为 0 时返回 0。
     */
    private Integer calculateProgressPercent(Integer finishedCount, Integer totalCount) {
        if (totalCount == null || totalCount <= 0 || finishedCount == null || finishedCount <= 0) {
            return 0;
        }
        int percent = finishedCount * 100 / totalCount;
        return Math.min(percent, 100);
    }

    /**
     * 将任务状态编码转换为中文说明。
     */
    private String taskStatusDesc(Integer status) {
        if (status == null) {
            return null;
        }
        for (TaskStatusEnums statusEnum : TaskStatusEnums.values()) {
            if (statusEnum.getCode().equals(status)) {
                return statusEnum.getDesc();
            }
        }
        return "未知状态";
    }

    /**
     * 将评测结果明细状态编码转换为中文说明。
     */
    private String evalResultStatusDesc(Integer status) {
        if (status == null) {
            return null;
        }
        for (EvalResultStatusEnums statusEnum : EvalResultStatusEnums.values()) {
            if (statusEnum.getCode().equals(status)) {
                return statusEnum.getDesc();
            }
        }
        return "未知状态";
    }

    /**
     * 将流水线节点编码转换为中文说明。
     */
    private String pipelineNodeCodeDesc(String nodeCode) {
        if (nodeCode == null) {
            return null;
        }
        for (PipelineNodeCodeEnums codeEnum : PipelineNodeCodeEnums.values()) {
            if (codeEnum.getCode().equals(nodeCode)) {
                return codeEnum.getDesc();
            }
        }
        return "未知节点";
    }

    /**
     * 将流水线节点状态转换为中文说明。
     */
    private String pipelineNodeStatusDesc(String status) {
        if (status == null) {
            return null;
        }
        for (PipelineNodeStatusEnums statusEnum : PipelineNodeStatusEnums.values()) {
            if (statusEnum.getCode().equals(status)) {
                return statusEnum.getDesc();
            }
        }
        return "未知状态";
    }

    /**
     * 解析 JSON 快照字段，解析失败时保留原始字符串，避免接口因单条脏数据整体失败。
     */
    private Object parseJsonSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        try {
            return JSON.parse(snapshot);
        } catch (Exception ex) {
            log.warn("流水线节点 JSON 快照解析失败，返回原始字符串，snapshot: {}", snapshot, ex);
            return snapshot;
        }
    }

    /**
     * 过滤空 ID，避免批量查询时传入无效参数。
     */
    private Set<Long> filterNonNullIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 获取分页页码，未传时默认第 1 页。
     */
    private long pageCurrent(Integer current) {
        return Optional.ofNullable(current)
                .filter(value -> value > 0)
                .map(Integer::longValue)
                .orElse(1L);
    }

    /**
     * 获取分页大小，未传时默认每页 10 条。
     */
    private long pageSize(Integer size) {
        return Optional.ofNullable(size)
                .filter(value -> value > 0)
                .map(Integer::longValue)
                .orElse(10L);
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
