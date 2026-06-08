package com.kant.llm.eval.service;

import com.kant.llm.eval.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.dto.req.CreateEvalTaskRequest;

import java.util.List;

public interface EvalTaskService {
    void createEvalTask(CreateEvalTaskRequest request);

    Boolean submitEvalTask(Long taskId);

    /**
     * 终止当前评测任务的活跃执行批次。
     *
     * <p>该操作只终止当前批次，不删除已完成结果，也不支持恢复；后续重新评测需要再次提交任务。</p>
     *
     * @param taskId 评测任务 ID
     * @return 是否成功处理停止请求
     */
    Boolean stopEvalTask(Long taskId);

    List<EvalTaskDetailDO> selectPendingTasks();
}
