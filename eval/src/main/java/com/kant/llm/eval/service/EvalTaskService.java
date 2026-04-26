package com.kant.llm.eval.service;

import com.kant.llm.eval.dto.req.CreateEvalTaskRequest;

public interface EvalTaskService {
    void createEvalTask(CreateEvalTaskRequest request);

    void submitEvalTask(Long taskId);
}
