package com.chinatelecom.aigc.evaluate.controller;


import com.chinatelecom.aigc.evaluate.common.pojo.CommonResult;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.common.util.object.BeanUtils;
import com.chinatelecom.aigc.evaluate.domain.JobDO;
import com.chinatelecom.aigc.evaluate.dto.req.JobPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.JobSaveReq;
import com.chinatelecom.aigc.evaluate.dto.req.JobUpdateReq;
import com.chinatelecom.aigc.evaluate.dto.resp.JobResp;
import com.chinatelecom.aigc.evaluate.service.JobService;
import com.google.common.collect.Sets;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.quartz.SchedulerException;
import org.quartz.impl.jdbcjobstore.Constants;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.chinatelecom.aigc.evaluate.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/aigc/evaluate/job")
@Api(tags = "任务管理-job")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/create")
    @ApiOperation("创建任务")
    public CommonResult<Long> createJob(@Valid @RequestBody JobSaveReq createReqVO) throws SchedulerException {
        return success(jobService.createJob(createReqVO));
    }


    @PutMapping("/update")
    @ApiOperation("更新任务")
    public CommonResult<Boolean> updateJob(@Valid @RequestBody JobUpdateReq updateReqVO)
            throws SchedulerException {
        jobService.updateJob(updateReqVO);
        return CommonResult.success("任务更新成功", 0);
    }

    @PutMapping("/stop")
    @ApiOperation("停止任务")
    public CommonResult<Boolean> stopJob(@ApiParam(value = "任务ID", required = true) @RequestParam(value = "id") Long id)
            throws SchedulerException {
        jobService.stopJob(id);
        return CommonResult.success("停止任务已经下发，等待上次任务停止", 0);
    }

    @PutMapping("/trigger")
    @ApiOperation("触发任务")
    public CommonResult<Boolean> triggerJob(@ApiParam(value = "任务ID", required = true) @RequestParam("id") Long id) throws SchedulerException {
        jobService.triggerJob(id);
        return CommonResult.success("任务开始执行", 0);
    }

    @GetMapping("/get")
    @ApiOperation("获得任务")
    public CommonResult<JobResp> getJob(@ApiParam(value = "任务ID", required = true) @RequestParam("id") Long id) {
        JobResp jobResp = jobService.getJob(id);
        return success(BeanUtils.toBean(jobResp, JobResp.class));
    }

    @GetMapping("/delete")
    @ApiOperation("删除任务")
    public CommonResult<Boolean> deleteJob(@ApiParam(value = "任务ID", required = true) @RequestParam("id") Long id) throws SchedulerException {
        jobService.deleteJob(id);
        return CommonResult.success("任务删除成功", 0);
    }

    @GetMapping("/page")
    @ApiOperation("获得任务分页")
    public CommonResult<PageResult<JobResp>> getJobPage(@Valid JobPageReq pageVO) {
        PageResult<JobResp> jobRespPage = jobService.getJobPage(pageVO);
        return success(BeanUtils.toBean(jobRespPage, JobResp.class));
    }

    /**
     * 手动执行任务
     * @param id
     * @return
     */
    @GetMapping("/executeTaskJob")
    @ApiOperation("手动执行任务")
    public CommonResult<Boolean> executeTaskJob(@ApiParam(value = "任务ID", required = true) @RequestParam("id") Long id){
        Boolean result = jobService.executeTaskJob(id);
        return success(result);
    }

    /**
     * 手动执行任务,消息队列版本
     * @param id
     * @return
     */
    @GetMapping("/executeTaskJobMsg")
    @ApiOperation("手动执行任务")
    public CommonResult<Boolean> executeTaskJobMsg(@ApiParam(value = "任务ID", required = true) @RequestParam("id") Long id){
        Boolean result = jobService.executeTaskJobMsg(id);
        return success(result);
    }

}

