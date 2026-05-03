package com.kant.llm.eval.controller;

import com.kant.llm.eval.dto.req.CreateEvalTaskRequest;
import com.kant.llm.eval.service.EvalTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/eval-task")
public class EvalTaskController {

    private final EvalTaskService evalTaskService;

    public EvalTaskController(EvalTaskService evalTaskService) {
        this.evalTaskService = evalTaskService;
    }

    /**
     * 创建评测任务
     * @param request 创建评测任务请求参数
     * @return 无返回数据
     */
    @PostMapping("/create")
    public void createEvalTask(@RequestBody CreateEvalTaskRequest request) {
        evalTaskService.createEvalTask(request);
    }

    /**
     * 发起评测任务
     * @param taskId 评测任务ID
     * @return 无返回数据
     */
    @GetMapping("/submit")
    public void submitEvalTask(@RequestParam("taskId") Long taskId) {
        evalTaskService.submitEvalTask(taskId);
    }

    /**
     * 获取评测任务进度
     * @param taskId 评测任务ID
     * @return 无返回数据
     */
    @GetMapping("/progress")
    public void getEvalTaskProgress(@RequestParam("taskId") Long taskId) {
//        evalTaskService.getEvalTaskProgress(taskId);
    }
}
