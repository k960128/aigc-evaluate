package com.kant.llm.eval.agent.probe.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.agent.probe.application.ProbeApplicationService;
import com.kant.llm.eval.agent.probe.dto.resp.TargetCapabilitiesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 被测目标能力查询接口。
 */
@RestController
@RequestMapping("/agent/targets")
public class AgentTargetController {

    private final ProbeApplicationService applicationService;

    public AgentTargetController(ProbeApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询模型目标当前快照、支持能力和已知限制。
     */
    @GetMapping("/models/{modelId}/capabilities")
    public Result<TargetCapabilitiesResponse> getModelCapabilities(@PathVariable Long modelId) {
        return Results.success(TargetCapabilitiesResponse.from(
                applicationService.getModelCapabilities(modelId)));
    }
}
