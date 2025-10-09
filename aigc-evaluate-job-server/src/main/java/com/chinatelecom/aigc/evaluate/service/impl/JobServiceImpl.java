package com.chinatelecom.aigc.evaluate.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.common.util.object.BeanUtils;
import com.chinatelecom.aigc.evaluate.domain.JobDO;
import com.chinatelecom.aigc.evaluate.domain.ModelInfoDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetInfoDO;
import com.chinatelecom.aigc.evaluate.dto.req.JobPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.JobSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.JobUpdateReq;
import com.chinatelecom.aigc.evaluate.dto.resp.JobResp;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.chinatelecom.aigc.evaluate.handler.ExecuteJobHandler;
import com.chinatelecom.aigc.evaluate.job.core.handler.JobHandler;
import com.chinatelecom.aigc.evaluate.job.core.scheduler.SchedulerManager;
import com.chinatelecom.aigc.evaluate.job.enums.job.JobRunTypeEnum;
import com.chinatelecom.aigc.evaluate.job.enums.job.JobStatusEnum;
import com.chinatelecom.aigc.evaluate.job.utils.JobUtil;
import com.chinatelecom.aigc.evaluate.mapper.JobMapper;
import com.chinatelecom.aigc.evaluate.mapper.ModelInfoMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionSetInfoMapper;
import com.chinatelecom.aigc.evaluate.service.JobService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.chinatelecom.aigc.evaluate.common.exception.enums.ErrorCodeConstants.*;
import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;
import static com.chinatelecom.aigc.evaluate.common.util.collection.CollectionUtils.containsAny;
import static com.chinatelecom.aigc.evaluate.job.enums.error.ErrorCodeConstants.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Slf4j
@Service
public class JobServiceImpl implements JobService {

    public static final String HANDLER_NAME = "taskJobHandler";

    private final JobMapper jobMapper;
    private final SchedulerManager schedulerManager;

    private ModelInfoMapper modelInfoMapper;

    private QuestionSetInfoMapper questionSetInfoMapper;

    private final ExecuteJobHandler executeJobHandler;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public JobServiceImpl(JobMapper jobMapper,
                          SchedulerManager schedulerManager,
                          ModelInfoMapper modelInfoMapper,
                          QuestionSetInfoMapper questionSetInfoMapper,
                          ExecuteJobHandler executeJobHandler) {
        this.jobMapper = jobMapper;
        this.schedulerManager = schedulerManager;
        this.modelInfoMapper = modelInfoMapper;
        this.questionSetInfoMapper = questionSetInfoMapper;
        this.executeJobHandler = executeJobHandler;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createJob(JobSaveReq createReqVO) throws SchedulerException {
        if (jobMapper.selectByName(createReqVO.getName()) != null) {
            throw exception(JOB_NAME_EXISTS_ERROR);
        }

        JobDO job = BeanUtils.toBean(createReqVO, JobDO.class);
        log.info("job: {}", job);

        //检测param中数据是否正确
//        validateModelInfoFromHandlerParam(job.getHandlerParam());

        String handlerName = HANDLER_NAME;

        // 1.2 校验 JobHandler 是否存在
        //validateJobHandlerExists(handlerName);

        job.setStatus(JobStatusEnum.INIT.getStatus()); // 任务初始状态为 STOP
        job.setHandlerName(handlerName);
        fillJobMonitorTimeoutEmpty(job);
        jobMapper.insert(job);

        // 判断是否使用 Cron 触发器
        if (Objects.equals(JobRunTypeEnum.SCHEDULED.getStatus(), createReqVO.getRunType())) {
            if (StringUtils.isNotBlank(createReqVO.getCronExpression())) {
                JobUtil.validateCronExpression(createReqVO.getCronExpression());
            }

            // 3.1 添加 Job 到 Quartz 中
            schedulerManager.addJob(
                    job.getId(),
                    job.getName(),
                    handlerName,
                    job.getHandlerParam(),
                    job.getCronExpression(),
                    job.getMaxThreadSize(),
                    null,
                    null
            );
            // 3.2 更新 JobDO
            JobDO updateObj = JobDO.builder().id(job.getId()).status(JobStatusEnum.NORMAL.getStatus()).build();
            jobMapper.updateById(updateObj);
        } else if (Objects.equals(JobRunTypeEnum.MANUAL.getStatus(), createReqVO.getRunType())) {
            // 3.2 仅创建 Job，不设置 Cron 触发器
            schedulerManager.addDurableJob(
                    job.getId(),
                    job.getName(),
                    handlerName,
                    job.getHandlerParam(),
                    job.getMaxThreadSize(),
                    job.getRetryCount(),
                    job.getRetryInterval()
            );
            jobMapper.updateById(JobDO.builder().id(job.getId()).status(JobStatusEnum.NORMAL.getStatus()).build());
        } else if (Objects.equals(JobRunTypeEnum.ONE_TIME.getStatus(), createReqVO.getRunType())) {
            if (StringUtils.isBlank(createReqVO.getOneTimeExpression())) {
                throw exception(JOB_ONETIME_EXPRESSION_NOT_EXISTS_ERROR);
            }

            // 解析时间
            LocalDateTime oneTime;
            try {
                oneTime = LocalDateTime.parse(createReqVO.getOneTimeExpression(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException e) {
                throw exception(JOB_ONETIME_EXPRESSION_INVALID_ERROR);
            }

            // 校验时间必须大于当前时间
            if (oneTime.isBefore(LocalDateTime.now())) {
                throw exception(JOB_ONETIME_EXPRESSION_PAST_ERROR);
            }

            schedulerManager.addOneTimeJob(
                    job.getId(),
                    job.getName(),
                    handlerName,
                    job.getHandlerParam(),
                    createReqVO.getOneTimeExpression(),
                    job.getMaxThreadSize()
            );
            jobMapper.updateById(JobDO.builder().id(job.getId()).status(JobStatusEnum.NORMAL.getStatus()).build());
        }

        // 3.2 数据库中状态设为 STOP，表示任务未执行
        return job.getId();
    }

    /**
     * 从 handlerParam 解析 modelId 和 questionSet 并进行校验
     *
     * @param handlerParam JSON 字符串，如 {"modelId":[6,7],"questionSet":[6]}
     */
    public void validateModelInfoFromHandlerParam(String handlerParam) {
        if (handlerParam == null || handlerParam.isEmpty()) {
            throw exception(JOB_PARAM_ERROR);
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(handlerParam);

            // 校验 modelId 字段
            if (!jsonNode.has("modelId")) {
                throw exception(JOB_PARAM_ERROR);
            }
            JsonNode modelIdsNode = jsonNode.get("modelId");

            // 确保 modelId 是一个非空数组
            if (!modelIdsNode.isArray() || modelIdsNode.isEmpty()) {
                throw exception(JOB_PARAM_ERROR);
            }

            // 校验 questionSet 字段
            if (!jsonNode.has("questionSet")) {
                throw exception(JOB_PARAM_ERROR);
            }
            JsonNode questionSetNode = jsonNode.get("questionSet");

            // 确保 questionSet 是一个非空数组
            if (!questionSetNode.isArray() || questionSetNode.isEmpty()) {
                throw exception(JOB_PARAM_ERROR);
            }

            // 遍历所有的 modelId 和 questionSet
            for (JsonNode modelIdNode : modelIdsNode) {
                Long modelId = modelIdNode.asLong();
                log.info("处理 modelId: {}", modelId);

                ModelInfoDO modelHandler = modelInfoMapper.selectById(modelId);
                if (modelHandler == null) {
                    throw exception(MODEL_NOT_EXISTS_ERROR);
                }
            }

            // 遍历所有的 questionSet
            for (JsonNode questionSetNodeItem : questionSetNode) {
                Long questionSetId = questionSetNodeItem.asLong();
                log.info("处理 questionSetId: {}", questionSetId);

                QuestionSetInfoDO questionSetInfoDO = questionSetInfoMapper.selectById(questionSetId);
                if (questionSetInfoDO == null) {
                    throw exception(QUEUSTION_SET_NOT_EXISTS_ERROR);
                }
            }

        } catch (Exception e) {
            throw exception(JOB_PARAM_ERROR);
        }
    }


    @Override
    public PageResult<JobResp> getJobPage(JobPageReq pageReqVO) {
        // 查询分页数据
        PageResult<JobDO> pageResult = jobMapper.selectPage(pageReqVO);

        // 解析数据并填充 `modelInfo` 和 `questionSet`
        List<JobResp> jobRespList = pageResult.getList().stream().map(job -> {
            JobResp jobResp = BeanUtils.toBean(job, JobResp.class);
            try {
                JsonNode jsonNode = objectMapper.readTree(job.getHandlerParam());
                List<Long> modelInfoset = new ArrayList<>();
                if (jsonNode.has("modelId") && jsonNode.get("modelId").isArray()) {
                    for (JsonNode node : jsonNode.get("modelId")) {
                        modelInfoset.add(node.asLong());
                    }
                    List<ModelInfoDO> modelInfoSetList = modelInfoMapper.selectList(
                            new LambdaQueryWrapper<ModelInfoDO>()
                                    .in(ModelInfoDO::getId, modelInfoset)
                                    .orderByDesc(ModelInfoDO::getCreateTime, ModelInfoDO::getUpdateTime)
                    );
                    jobResp.setModelInfoSet(modelInfoSetList);
                }

                // 处理 questionSet
                List<Long> questionSet = new ArrayList<>();
                if (jsonNode.has("questionSet") && jsonNode.get("questionSet").isArray()) {
                    for (JsonNode node : jsonNode.get("questionSet")) {
                        questionSet.add(node.asLong());
                    }
                    if (questionSet != null && !questionSet.isEmpty()) {
                        List<QuestionSetInfoDO> questionSetList = questionSetInfoMapper.selectList(
                                new LambdaQueryWrapper<QuestionSetInfoDO>()
                                        .in(QuestionSetInfoDO::getId, questionSet)
                                        .orderByDesc(QuestionSetInfoDO::getCreateTime, QuestionSetInfoDO::getUpdateTime)
                        );
                        jobResp.setQuestionSet(questionSetList);
                    } else {
                        // 处理为空的情况
                        log.warn("questionSet is empty, skipping query.");
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("解析 handlerParam 失败", e);
            }
            return jobResp;
        }).collect(Collectors.toList());

        // 组装分页结果并返回
        return new PageResult<>(jobRespList, pageResult.getTotal());
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateJob(JobUpdateReq updateReqVO) throws SchedulerException {
        JobDO job = jobMapper.selectById(updateReqVO.getId());
        if (job == null) {
            throw exception(JOB_NOT_EXISTS_ERROR);
        }

        if (!Objects.equals(job.getName(), updateReqVO.getName())) {
            if (jobMapper.selectByName(updateReqVO.getName()) != null) {
                throw exception(JOB_NAME_EXISTS_ERROR);
            }
        }

        if (job.getStatus().equals(JobStatusEnum.EXECUTING.getStatus()) ||
                job.getStatus().equals(JobStatusEnum.PAUSED.getStatus())) {
            throw exception(JOB_CANNOT_UPDATE_BY_THIS_STATUS);
        }

        // 1.2 只有开启状态，才可以修改.原因是，如果出暂停状态，修改 Quartz Job 时，会导致任务又开始执行
        //if (!job.getStatus().equals(JobStatusEnum.INIT.getStatus()))) {
        //    throw exception(JOB_UPDATE_ONLY_NORMAL_STATUS);
        //}

        // 2. 更新 JobDO
        JobDO updateObj = BeanUtils.toBean(updateReqVO, JobDO.class);
        log.info("updateObj: {}", updateObj);
        String handlerName = HANDLER_NAME;

        validateModelInfoFromHandlerParam(updateObj.getHandlerParam());
        fillJobMonitorTimeoutEmpty(updateObj);
        jobMapper.updateById(updateObj);

        schedulerManager.deleteJob(String.valueOf(job.getName()));

        // 判断是否使用 Cron 触发器
        if (Objects.equals(JobRunTypeEnum.SCHEDULED.getStatus(), updateReqVO.getRunType())) {
            if (StringUtils.isNotBlank(updateReqVO.getCronExpression())) {
                JobUtil.validateCronExpression(updateReqVO.getCronExpression());
            }

            // 3.1 添加 Job 到 Quartz 中
            schedulerManager.addJob(
                    job.getId(),
                    updateReqVO.getName(),
                    handlerName,
                    updateReqVO.getHandlerParam(),
                    updateReqVO.getCronExpression(),
                    job.getMaxThreadSize(),
                    null,
                    null
            );
            // 3.2 更新 JobDO
            JobDO updateJob = JobDO.builder().id(job.getId()).status(JobStatusEnum.NORMAL.getStatus()).build();
            jobMapper.updateById(updateJob);
        } else if (Objects.equals(JobRunTypeEnum.MANUAL.getStatus(), updateReqVO.getRunType())) {
            // 3.2 仅创建 Job，不设置 Cron 触发器
            schedulerManager.addDurableJob(
                    job.getId(),
                    updateReqVO.getName(),
                    handlerName,
                    updateReqVO.getHandlerParam(),
                    job.getMaxThreadSize(),
                    null,
                    null
            );
            jobMapper.updateById(JobDO.builder().id(job.getId()).status(JobStatusEnum.NORMAL.getStatus()).build());
        } else if (Objects.equals(JobRunTypeEnum.ONE_TIME.getStatus(), updateReqVO.getRunType())) {
            if (StringUtils.isBlank(updateReqVO.getOneTimeExpression())) {
                throw exception(JOB_ONETIME_EXPRESSION_NOT_EXISTS_ERROR);
            }

            // 解析时间
            LocalDateTime oneTime;
            try {
                oneTime = LocalDateTime.parse(updateReqVO.getOneTimeExpression(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException e) {
                throw exception(JOB_ONETIME_EXPRESSION_INVALID_ERROR);
            }

            // 校验时间必须大于当前时间
            if (oneTime.isBefore(LocalDateTime.now())) {
                throw exception(JOB_ONETIME_EXPRESSION_PAST_ERROR);
            }

            schedulerManager.addOneTimeJob(
                    job.getId(),
                    updateReqVO.getName(),
                    handlerName,
                    updateReqVO.getHandlerParam(),
                    updateReqVO.getOneTimeExpression(),
                    job.getMaxThreadSize()
            );
            jobMapper.updateById(JobDO.builder().id(job.getId()).status(JobStatusEnum.NORMAL.getStatus()).build());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stopJob(Long id) throws SchedulerException {
        // 校验存在
        JobDO job = validateJobExists(id);
        if (!job.getStatus().equals(JobStatusEnum.EXECUTING.getStatus())) {
            throw exception(JOB_UPDATE_ONLY_EXECUTING_STATUS);
        }

        // 更新 Job 状态
        JobDO updateObj = JobDO.builder().id(id).status(JobStatusEnum.PAUSED.getStatus()).build();
        jobMapper.updateById(updateObj);

        /*
        // 更新状态 Job 到 Quartz 中
        if (JobStatusEnum.NORMAL.getStatus().equals(status)) { // 开启
            schedulerManager.resumeJob(job.getHandlerName());
        } else { // 暂停
            schedulerManager.pauseJob(job.getHandlerName());
        }
         */
    }

    /*
    @Override
    public void triggerJob(Long id) throws SchedulerException {
        // 校验存在
        JobDO job = validateJobExists(id);

        // 触发 Quartz 中的 Job
        schedulerManager.triggerJob(job.getId(), job.getHandlerName(), job.getHandlerParam());
    }
     */

    @Override
    public void triggerJob(Long id) throws SchedulerException {
        // 校验任务是否存在
        JobDO job = validateJobExists(id);
        log.info("name: {} status: {}", job.getName(), job.getStatus());

        if (job.getStatus().equals(JobStatusEnum.EXECUTING.getStatus())) {
            throw exception(JOB_IS_EXECUTING);
        }

        if (job.getStatus().equals(JobStatusEnum.PAUSED.getStatus())) {
            throw exception(JOB_WAITING_STOP);
        }

        if (Objects.equals(JobRunTypeEnum.MANUAL.getStatus(), job.getRunType())) {

            log.info("Triggering job - name: {}, handler: {}", job.getName(), job.getHandlerName());

            // 触发 Quartz 中的 Job
            schedulerManager.triggerJob(
                    job.getId(),
                    job.getName(),
                    job.getHandlerName(),
                    job.getHandlerParam(),
                    job.getMaxThreadSize(),
                    job.getRetryCount(),
                    job.getRetryInterval()
            );
        } else {
            throw exception(JOB_CANNET_TRIGGER);
        }
    }


    @Override
    public JobResp getJob(Long id) {
        JobDO job = jobMapper.selectById(id);
        if (job == null) {
            throw exception(JOB_NOT_EXISTS);
        }
        JobResp jobResp = BeanUtils.toBean(job, JobResp.class);

        try {
            // 组装 JobResp
            JsonNode jsonNode = objectMapper.readTree(job.getHandlerParam());

            List<Long> modelIdSet = new ArrayList<>();
            if (jsonNode.has("modelId") && jsonNode.get("modelId").isArray()) {
                for (JsonNode node : jsonNode.get("modelId")) {
                    modelIdSet.add(node.asLong());
                }

                List<ModelInfoDO> modelInfoSetList = modelInfoMapper.selectList(
                        new LambdaQueryWrapper<ModelInfoDO>()
                                .in(ModelInfoDO::getId, modelIdSet)
                );

                jobResp.setModelInfoSet(modelInfoSetList);

                jobResp.setModelInfoMap(Optional.ofNullable(modelInfoSetList)
                        .filter(list -> !list.isEmpty())
                        .map(list -> list.stream()
                                .collect(Collectors.toMap(ModelInfoDO::getId, Function.identity())))
                        .orElseGet(HashMap::new));
            }

            List<Long> questionSet = new ArrayList<>();
            if (jsonNode.has("questionSet") && jsonNode.get("questionSet").isArray()) {
                for (JsonNode node : jsonNode.get("questionSet")) {
                    questionSet.add(node.asLong());
                }

                List<QuestionSetInfoDO> questionSetList = questionSetInfoMapper.selectList(
                        new LambdaQueryWrapper<QuestionSetInfoDO>()
                                .in(QuestionSetInfoDO::getId, questionSet)
                );

                jobResp.setQuestionSet(questionSetList);
            }
        } catch (Exception e) {
            throw new RuntimeException("解析 handlerParam 失败", e);
        }

        return jobResp;
    }

    @Override
    public void deleteJob(Long id) throws SchedulerException {
        // 1. 校验任务是否存在
        JobDO job = validateJobExists(id);

        if (job.getStatus().equals(JobStatusEnum.PAUSED.getStatus())) {
            throw exception(JOB_WAITING_STOP);
        }

        // 2. 如果任务是运行状态，先暂停再删除
        if (job.getStatus().equals(JobStatusEnum.EXECUTING.getStatus())) {
            throw exception(JOB_PLEASE_STOP);
            //schedulerManager.pauseJob(job.getName());
        }

        // 3. 从 Quartz 中删除任务
        schedulerManager.deleteJob(job.getName());

        // 4. 从数据库中删除任务
        jobMapper.deleteById(id);

        log.info("任务 {} 删除成功", id);
    }


    private JobDO validateJobExists(Long id) {
        JobDO job = jobMapper.selectById(id);
        if (job == null) {
            throw exception(JOB_NOT_EXISTS);
        }
        return job;
    }

    private static void fillJobMonitorTimeoutEmpty(JobDO job) {
        if (job.getMonitorTimeout() == null) {
            job.setMonitorTimeout(0);
        }
    }

    private void validateJobHandlerExists(String handlerName) {
        try {
            log.info("handlerName: {}", handlerName);
            Object handler = SpringUtil.getBean(handlerName);
            assert handler != null;
            if (!(handler instanceof JobHandler)) {
                throw exception(JOB_HANDLER_BEAN_TYPE_ERROR);
            }
        } catch (NoSuchBeanDefinitionException e) {
            throw exception(JOB_HANDLER_BEAN_NOT_EXISTS);
        }
    }

    public List<String> getJobIdsByQuestionSetId(Long questionSetId) {
        List<JobDO> allJobs = jobMapper.selectList(new LambdaQueryWrapperX<JobDO>());
        List<String> jobNames = new ArrayList<>();

        for (JobDO jobInfo : allJobs) {
            try {
                JsonNode handlerParamNode = objectMapper.readTree(jobInfo.getHandlerParam());
                JsonNode questionSetNode = handlerParamNode.path("questionSet");

                // 检查 questionSet 是否包含目标的 questionSetId
                if (containsId(questionSetNode, questionSetId)) {
                    jobNames.add(jobInfo.getName());
                }
            } catch (Exception e) {
                throw exception(JOB_PARAM_ERROR);
            }
        }

        return jobNames;
    }

    public List<String> getJobIdsByModelInfoId(Long modelInfoId) {
        List<JobDO> allJobs = jobMapper.selectList(new LambdaQueryWrapperX<JobDO>());
        List<String> jobNames = new ArrayList<>();

        for (JobDO jobInfo : allJobs) {
            try {
                JsonNode handlerParamNode = objectMapper.readTree(jobInfo.getHandlerParam());
                JsonNode modelIdNode = handlerParamNode.path("modelId");

                if (containsId(modelIdNode, modelInfoId)) {
                    jobNames.add(jobInfo.getName());
                }
            } catch (Exception e) {
                throw exception(JOB_PARAM_ERROR);
            }
        }

        return jobNames;
    }

    private boolean containsId(JsonNode setNode, Long setId) {
        if (setNode.isArray()) {
            for (JsonNode node : setNode) {
                if (node.asLong() == setId) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Boolean executeTaskJob(Long id) {
        JobResp job = getJob(id);
        executeJobHandler.execute1(job);
//        new Thread(() -> executeJobHandler.execute(job)).start();
        return true;
    }

    @Override
    public Boolean executeTaskJobMsg(Long id) {
        JobResp job = getJob(id);
        executeJobHandler.executeMsg(job);
        return true;
    }
}
