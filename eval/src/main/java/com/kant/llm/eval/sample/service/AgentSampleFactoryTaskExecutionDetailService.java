package com.kant.llm.eval.sample.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.sample.dao.entity.AgentSampleFactoryTaskExecutionDetailDO;
import com.kant.llm.eval.sample.dto.SampleFactoryTaskExecutionDetailPageRequest;
import com.kant.llm.eval.sample.dto.SampleFactoryTaskExecutionDetailVO;

import java.util.List;

public interface AgentSampleFactoryTaskExecutionDetailService extends IService<AgentSampleFactoryTaskExecutionDetailDO> {

    /**
     * 根据任务ID查询执行明细，按创建时间和ID倒序排列。
     *
     * @param taskId 样本生产任务ID，对应任务表的task_id
     * @return 执行明细列表，无明细时返回空列表
     */
    List<SampleFactoryTaskExecutionDetailVO> listSampleFactoryTaskExecutionDetails(String taskId);

    /**
     * 根据任务ID分页查询执行明细，按创建时间和ID倒序排列。
     *
     * @param request 任务明细分页查询参数
     * @return 执行明细分页结果
     */
    Page<SampleFactoryTaskExecutionDetailVO> pageSampleFactoryTaskExecutionDetails(SampleFactoryTaskExecutionDetailPageRequest request);
}
