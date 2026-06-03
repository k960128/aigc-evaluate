package com.kant.llm.eval.service;

import com.kant.llm.eval.dao.entity.EvalTaskDetailDO;
import com.kant.llm.eval.dto.req.CreateEvalTaskRequest;

import java.util.List;

public interface EvalTaskService {
    void createEvalTask(CreateEvalTaskRequest request);

    Boolean submitEvalTask(Long taskId);

    List<EvalTaskDetailDO> selectPendingTasks();
}
