package com.kant.agent.controller;

import com.kant.agent.application.RedTeamEvaluateApplicationService;
import com.kant.agent.contract.RedTeamEvaluateRequest;
import com.kant.agent.contract.RedTeamTaskResult;
import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import org.springframework.web.bind.annotation.*;

/**
 * 第一阶段的同步红队评测接口。
 *
 * <p>该 Controller 只负责 HTTP 入参出参适配，不直接编排智能体。M1 使用同步接口，
 * 前端提交请求后会直接拿到完整结果；后续异步化时可以在相同路径下扩展任务提交和事件查询。</p>
 */
@RestController
@RequestMapping("/eval-agent")
public class RedTeamEvaluateController {

    /** 处理红队评测用例的应用服务。 */
    private final RedTeamEvaluateApplicationService applicationService;

    /**
     * 创建红队评测 Controller。
     *
     * @param applicationService 红队评测应用服务
     */
    public RedTeamEvaluateController(RedTeamEvaluateApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 提交并同步执行一次红队评测。
     *
     * @param request 前端提交的评测请求
     * @return 统一响应包装后的评测结果
     */
    @PostMapping("/evaluate")
    public Result<RedTeamTaskResult> evaluate(@RequestBody RedTeamEvaluateRequest request) {
        return Results.success(applicationService.evaluate(request));
    }

    /**
     * 根据任务 ID 查询已经保存的评测结果。
     *
     * @param taskId 路径中的任务标识
     * @return 统一响应包装后的评测结果；未找到时沿用当前应用服务的空值语义
     */
    @GetMapping("/evaluate/{taskId}")
    public Result<RedTeamTaskResult> findByTaskId(@PathVariable String taskId) {
        return Results.success(applicationService.findByTaskId(taskId));
    }
}
