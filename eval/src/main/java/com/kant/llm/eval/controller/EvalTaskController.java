package com.kant.llm.eval.controller;

import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.web.Results;
import com.kant.llm.eval.dto.req.CreateEvalTaskRequest;
import com.kant.llm.eval.service.EvalTaskService;
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
    @PostMapping("/submit")
    public Result<Boolean> submitEvalTask(@RequestParam("taskId") Long taskId) {
        Boolean b = evalTaskService.submitEvalTask(taskId);
        return Results.success(b);
    }

    /**
     * 终止评测任务当前批次
     *
     * <p>停止后会保留已完成样本结果，未开始样本不再执行；如需重新评测，需要重新提交任务。</p>
     *
     * @param taskId 评测任务ID
     * @return 停止请求处理结果
     */
    @PostMapping("/stop")
    public Result<Boolean> stopEvalTask(@RequestParam("taskId") Long taskId) {
        Boolean stopped = evalTaskService.stopEvalTask(taskId);
        return Results.success(stopped);
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
