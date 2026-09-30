package com.kant.llm.eval.agent.redteam.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.agent.redteam.application.RedTeamApplicationService;
import com.kant.llm.eval.agent.redteam.dto.req.CreateRedTeamTaskRequest;
import com.kant.llm.eval.agent.redteam.dto.resp.RedTeamRunResponse;
import com.kant.llm.eval.agent.redteam.dto.resp.RedTeamTaskResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * D02 自动红队任务接口。
 *
 * <p>当前启动接口同步推进一条小规模运行，用于先验证业务闭环；运行事实已经
 * 持久化，后续切换后台 Worker 时保持接口和查询模型不变。</p>
 */
@RestController
@RequestMapping("/agent/red-team")
public class RedTeamController {

    private final RedTeamApplicationService applicationService;

    public RedTeamController(RedTeamApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 创建或幂等返回一份自动红队任务配置。 */
    @PostMapping("/tasks")
    public Result<RedTeamTaskResponse> createTask(
            @Valid @RequestBody CreateRedTeamTaskRequest request) {
        return Results.success(applicationService.createTask(request));
    }

    /** 查询任务配置和任务汇总状态。 */
    @GetMapping("/tasks/{taskId}")
    public Result<RedTeamTaskResponse> getTask(@PathVariable String taskId) {
        return Results.success(applicationService.getTask(taskId));
    }

    /** 启动一次新的红队运行。 */
    @PostMapping("/tasks/{taskId}/runs")
    public Result<RedTeamRunResponse> startRun(@PathVariable String taskId) {
        return Results.success(applicationService.startRun(taskId));
    }

    /** 查询运行状态、动作轨迹和疑似发现。 */
    @GetMapping("/runs/{runId}")
    public Result<RedTeamRunResponse> getRun(@PathVariable String runId) {
        return Results.success(applicationService.getRun(runId));
    }

    /** 记录用户停止意图。已发出的目标请求仍可能返回 UNKNOWN 或迟到响应。 */
    @PostMapping("/runs/{runId}/stop")
    public Result<RedTeamRunResponse> requestStop(@PathVariable String runId) {
        return Results.success(applicationService.requestStop(runId));
    }
}
