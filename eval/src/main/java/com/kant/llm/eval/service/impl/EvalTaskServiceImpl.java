package com.kant.llm.eval.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.llm.eval.common.enums.TaskStatusEnums;
import com.kant.llm.eval.dao.entity.DataSetSampleDO;
import com.kant.llm.eval.dao.entity.EvalTaskDO;
import com.kant.llm.eval.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.dao.entity.ModelInfoDO;
import com.kant.llm.eval.dao.mapper.DataSetSampleMapper;
import com.kant.llm.eval.dao.mapper.EvalTaskDetailMapper;
import com.kant.llm.eval.dao.mapper.EvalTaskMapper;
import com.kant.llm.eval.dao.mapper.ModelInfoMapper;
import com.kant.llm.eval.dto.req.CreateEvalTaskRequest;
import com.kant.llm.eval.service.EvalTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class EvalTaskServiceImpl implements EvalTaskService {
    private final EvalTaskMapper evalTaskMapper;
    private final ModelInfoMapper modelInfoMapper;
    private final DataSetSampleMapper dataSetSampleMapper;
    private final EvalTaskDetailMapper evalTaskDetailMapper;

    public EvalTaskServiceImpl(EvalTaskMapper evalTaskMapper,
                               ModelInfoMapper modelInfoMapper,
                               DataSetSampleMapper dataSetSampleMapper,
                               EvalTaskDetailMapper evalTaskDetailMapper) {
        this.evalTaskMapper = evalTaskMapper;
        this.modelInfoMapper = modelInfoMapper;
        this.dataSetSampleMapper = dataSetSampleMapper;
        this.evalTaskDetailMapper = evalTaskDetailMapper;
    }

    @Override
    public void createEvalTask(CreateEvalTaskRequest request) {
        log.info("创建评测任务: {}", request);
    }

    @Override
    public void submitEvalTask(Long taskId) {
        String evalSerial = IdUtil.getSnowflake().nextIdStr();
        // 1. 获取任务详情
        EvalTaskDO task = evalTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务状态非法，无法启动");
        }
        // 2. 获取数据集
        Long sampleCount = dataSetSampleMapper.selectCount(new LambdaQueryWrapper<>(DataSetSampleDO.class)
                .eq(DataSetSampleDO::getDatasetId, task.getDatasetId()));
        // 3. 创建任务表详细信息
        EvalTaskDetailDO evalTaskDetailDO = EvalTaskDetailDO.builder()
                .id(IdUtil.getSnowflake().nextId())
                .serialNo(IdUtil.getSnowflake().nextId())
                .taskName(task.getTaskName())
                .modelId(task.getModelId())
                .datasetId(task.getDatasetId())
                .status(1)
                .totalCount(Math.toIntExact(sampleCount))
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        evalTaskDetailMapper.insert(evalTaskDetailDO);
    }
}
