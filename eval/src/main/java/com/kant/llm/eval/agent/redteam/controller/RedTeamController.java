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
 * 自动红队任务接口。
 *
 * <p>D02 单轮运行和 D03 多轮运行共用任务、运行查询入口；暂停和恢复只对
 * D03 多轮运行开放。</p>
 */
@RestController
@RequestMapping("/agent/red-team")
public class RedTeamController {

    private final RedTeamApplicationService applicationService;
    private final com.kant.llm.eval.agent.redteam.service.TaskCommandService commands;
    private final com.kant.llm.eval.agent.redteam.service.RunEventService events;

    public RedTeamController(RedTeamApplicationService applicationService,
            com.kant.llm.eval.agent.redteam.service.TaskCommandService commands,
            com.kant.llm.eval.agent.redteam.service.RunEventService events) {
        this.applicationService = applicationService;
        this.commands=commands; this.events=events;
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

    /** 请求 D03 多轮运行在当前动作边界保存检查点并暂停。 */
    @PostMapping("/runs/{runId}/pause")
    public Result<RedTeamRunResponse> requestPause(@PathVariable String runId) {
        return Results.success(applicationService.requestPause(runId));
    }

    /** 从 D03 最近一次持久化检查点恢复同一条运行和目标会话。 */
    @PostMapping("/runs/{runId}/resume")
    public Result<RedTeamRunResponse> resume(@PathVariable String runId) {
        return Results.success(applicationService.resumeRun(runId));
    }

    /** 推荐的新控制入口：提交幂等键与期望版本，返回首次受理回执。 */
    @PostMapping("/runs/{runId}/commands")
    public Result<com.kant.llm.eval.agent.redteam.dto.resp.RedTeamCommandReceipt> command(@PathVariable String runId,
            @Valid @RequestBody com.kant.llm.eval.agent.redteam.dto.req.RedTeamRunCommandRequest request) {
        return Results.success(commands.submit(runId,request));
    }
    /** 查询一致的状态版本和SSE续订起点。 */
    @GetMapping("/runs/{runId}/control-state")
    public Result<com.kant.llm.eval.agent.redteam.dto.resp.RedTeamControlState> controlState(@PathVariable String runId) {
        return Results.success(events.state(runId));
    }
    /** 分页拉取持久化事件；afterEventId=0从首个事件开始。 */
    @GetMapping("/runs/{runId}/event-history")
    public Result<java.util.List<com.kant.llm.eval.agent.redteam.dto.resp.RedTeamRunEventResponse>> eventHistory(@PathVariable String runId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="0") long afterEventId) {
        return Results.success(events.history(runId,afterEventId));
    }
    /** 浏览器重连时Last-Event-ID优先于初始查询参数；事件仅供展示。 */
    @GetMapping(value="/runs/{runId}/events",produces="text/event-stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(@PathVariable String runId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="0") long afterEventId,
            @org.springframework.web.bind.annotation.RequestHeader(value="Last-Event-ID",required=false) String lastEventId) {
        try { return events.subscribe(runId,lastEventId==null?afterEventId:Long.parseLong(lastEventId)); }
        catch (NumberFormatException ex) { throw new com.kant.framework.exception.ClientException("事件游标格式错误",
                com.kant.llm.eval.agent.redteam.error.AgentRedTeamErrorCode.INVALID_TASK_REQUEST); }
    }
}
