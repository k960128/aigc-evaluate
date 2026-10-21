package com.kant.llm.eval.sample.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.sample.dao.entity.AgentSampleFactoryTaskDO;
import com.kant.llm.eval.sample.dto.SampleFactoryTaskPageRequest;
import com.kant.llm.eval.sample.dto.SampleFactoryTaskVO;
import com.kant.llm.eval.sample.dto.SeedCreateTask;

import java.util.List;

public interface AgentSampleFactoryTaskService extends IService<AgentSampleFactoryTaskDO> {

    /**
     * 创建样本扩充任务，生成任务ID并将三组配置序列化后保存。
     *
     * @param request 样本扩充任务请求
     * @return 是否保存成功
     */
    boolean createSampleFactoryTask(SeedCreateTask request);

    /**
     * 查询样本扩充任务列表，按创建时间和ID倒序排列。
     *
     * @return 任务列表，无任务时返回空列表
     */
    List<SampleFactoryTaskVO> listSampleFactoryTasks();

    /**
     * 分页查询样本扩充任务列表，按创建时间和ID倒序排列。
     *
     * @param request 分页查询请求，页码和每页数量默认使用1、10
     * @return 任务分页结果
     */
    Page<SampleFactoryTaskVO> pageSampleFactoryTasks(SampleFactoryTaskPageRequest request);

    /**
     * 启动样本扩充任务，将状态从停用改为启用。
     *
     * @param taskId 任务ID
     * @return 是否启动成功
     */
    Boolean start(String taskId);
}
