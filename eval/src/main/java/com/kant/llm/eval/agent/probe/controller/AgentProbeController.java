package com.kant.llm.eval.agent.probe.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.agent.probe.application.ProbeApplicationService;
import com.kant.llm.eval.agent.probe.dto.req.SubmitProbeRequest;
import com.kant.llm.eval.agent.probe.dto.resp.EvidenceContentResponse;
import com.kant.llm.eval.agent.probe.dto.resp.ProbeCancellationResponse;
import com.kant.llm.eval.agent.probe.dto.resp.ProbeExecutionResponse;
import com.kant.llm.eval.agent.probe.dto.resp.ProbeSubmissionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * D01 共享探测执行接口。
 *
 * <p>提交接口当前同步等待单次模型调用，但执行意图和业务状态已经独立持久化。
 * 调用方超时后应使用相同幂等键重试提交或按 executionId 查询，禁止自行更换
 * 幂等键盲目重发目标请求。</p>
 */
@RestController
@RequestMapping("/agent/probes")
public class AgentProbeController {

    private final ProbeApplicationService applicationService;

    public AgentProbeController(ProbeApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 受理并执行一次静态单轮模型探测。
     *
     * @param request 受控探测参数
     * @return 物理执行引用、当前状态和是否首次受理
     */
    @PostMapping
    public Result<ProbeSubmissionResponse> submit(@Valid @RequestBody SubmitProbeRequest request) {
        return Results.success(ProbeSubmissionResponse.from(applicationService.submit(request)));
    }

    /**
     * 查询一次物理执行的状态、证据引用和消耗。
     */
    @GetMapping("/{executionId}")
    public Result<ProbeExecutionResponse> getExecution(@PathVariable String executionId) {
        return Results.success(ProbeExecutionResponse.from(
                applicationService.getExecution(executionId)));
    }

    /**
     * 读取属于指定执行的原始证据正文。
     *
     * <p>返回内容必须由前端按纯文本展示，不能把目标响应作为 HTML 执行。</p>
     */
    @GetMapping("/{executionId}/evidence/{evidenceRef}")
    public Result<EvidenceContentResponse> getEvidence(@PathVariable String executionId,
                                                       @PathVariable String evidenceRef) {
        return Results.success(EvidenceContentResponse.from(
                applicationService.getEvidence(executionId, evidenceRef)));
    }

    /**
     * 记录取消意图。已发出的远程模型请求不保证能够立即终止。
     */
    @PostMapping("/{executionId}/cancellation")
    public Result<ProbeCancellationResponse> requestCancellation(@PathVariable String executionId) {
        return Results.success(ProbeCancellationResponse.from(
                applicationService.requestCancellation(executionId)));
    }
}
