package com.chinatelecom.aigc.evaluate.controller;

import com.chinatelecom.aigc.evaluate.common.pojo.CommonResult;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.common.util.object.BeanUtils;
import com.chinatelecom.aigc.evaluate.domain.JobLogDO;
import com.chinatelecom.aigc.evaluate.dto.resp.JobLogPageResp;
import com.chinatelecom.aigc.evaluate.dto.resp.JobLogResp;
import com.chinatelecom.aigc.evaluate.service.JobLogService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

import static com.chinatelecom.aigc.evaluate.common.pojo.CommonResult.success;


@RestController
@RequestMapping("/aigc/evaluate/job-log")
@Api(tags = "任务日志管理-job")
public class JobLogController {
    private final JobLogService jobLogService;

    public JobLogController(JobLogService jobLogService) {
        this.jobLogService = jobLogService;
    }

    @GetMapping("/get")
    @ApiOperation("获得定时任务日志")
    public CommonResult<JobLogResp> getJobLog(@ApiParam(value = "任务ID", required = true) @RequestParam("id") Long id) {
        JobLogDO jobLog = jobLogService.getJobLog(id);
        return success(BeanUtils.toBean(jobLog, JobLogResp.class));
    }

    @GetMapping("/page")
    @ApiOperation("获得定时任务日志分页")
    public CommonResult<PageResult<JobLogResp>> getJobLogPage(@Valid JobLogPageResp pageVO) {
        PageResult<JobLogDO> pageResult = jobLogService.getJobLogPage(pageVO);
        return success(BeanUtils.toBean(pageResult, JobLogResp.class));
    }
}
