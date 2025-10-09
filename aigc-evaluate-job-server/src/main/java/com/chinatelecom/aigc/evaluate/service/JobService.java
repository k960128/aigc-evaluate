package com.chinatelecom.aigc.evaluate.service;

import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.JobDO;
import com.chinatelecom.aigc.evaluate.dto.req.JobPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.JobSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.JobUpdateReq;
import com.chinatelecom.aigc.evaluate.dto.resp.JobResp;
import org.quartz.SchedulerException;

import javax.validation.Valid;
import java.util.List;

public interface JobService {

    /**
     * 创建定时任务
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createJob(@Valid JobSaveReq createReqVO) throws SchedulerException;

    /**
     * 获取定时任务分页
     * @param pageReqVO
     * @return
     */
    PageResult<JobResp> getJobPage(JobPageReq pageReqVO);
    /**
     * 更新定时任务
     *
     * @param updateReqVO 更新信息
     */
    void updateJob(JobUpdateReq updateReqVO) throws SchedulerException;

    /**
     * 更新定时任务的状态
     *
     * @param id 任务编号
     */
    void stopJob(Long id) throws SchedulerException;

    /**
     * 触发定时任务
     *
     * @param id 任务编号
     */
    void triggerJob(Long id) throws SchedulerException;

    JobResp getJob(Long id);

    void deleteJob(Long id) throws SchedulerException;

    List<String> getJobIdsByQuestionSetId(Long questionSetId);

    List<String> getJobIdsByModelInfoId(Long modelInfoId);

    Boolean executeTaskJob(Long id);

    Boolean executeTaskJobMsg(Long id);
}
