package com.chinatelecom.aigc.evaluate.service;

import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.JobLogDO;
import com.chinatelecom.aigc.evaluate.dto.resp.JobLogPageResp;
import com.chinatelecom.aigc.evaluate.job.core.service.JobLogFrameworkService;

public interface JobLogService extends JobLogFrameworkService {

    /**
     * 获得定时任务
     *
     * @param id 编号
     * @return 定时任务
     */
    JobLogDO getJobLog(Long id);

    /**
     * 获得定时任务分页
     *
     * @param pageReqVO 分页查询
     * @return 定时任务分页
     */
    PageResult<JobLogDO> getJobLogPage(JobLogPageResp pageReqVO);

    /**
     * 清理 exceedDay 天前的任务日志
     *
     * @param exceedDay 超过多少天就进行清理
     * @param deleteLimit 清理的间隔条数
     */
    Integer cleanJobLog(Integer exceedDay, Integer deleteLimit);

}
