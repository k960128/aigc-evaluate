package com.kant.llm.eval.sample.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.sample.dao.entity.AgentSampleFactoryTaskDetailLogsDO;

/**
 * 样本扩充-任务详情执行批次详情日志 Service
 */
public interface AgentSampleFactoryTaskDetailLogsService extends IService<AgentSampleFactoryTaskDetailLogsDO> {

    /**
     * 在模型调用前创建 RUNNING 状态的执行日志。
     */
    void startExecution(String taskId, Long taskDetailId, String messageId, String type, String promptContent);

    /**
     * 更新响应原文、Token 用量、耗时及 SUCCESS/FAILED 状态。
     */
    void completeExecution(String messageId, String contentResponse, Integer tokenCount, int latency, boolean success);
}
