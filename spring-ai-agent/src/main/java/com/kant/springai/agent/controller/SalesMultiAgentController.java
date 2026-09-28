package com.kant.springai.agent.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.springai.agent.agent.sales.SalesSupervisorAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 销售 Supervisor 多 Agent Demo 接口。
 */
@Slf4j
@RestController
@RequestMapping("/sales/multi-agent")
public class SalesMultiAgentController {

    private final SalesSupervisorAgent salesSupervisorAgent;

    public SalesMultiAgentController(SalesSupervisorAgent salesSupervisorAgent) {
        this.salesSupervisorAgent = salesSupervisorAgent;
    }

    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("接收销售 Supervisor 请求: message={}", request.message());
        long startedAt = System.currentTimeMillis();

        SalesSupervisorAgent.SupervisorResponse response = salesSupervisorAgent.chat(
                request.message(),
                LocalDate.now().toString());

        long durationMs = System.currentTimeMillis() - startedAt;
        log.info("销售 Supervisor 请求完成: steps={}, durationMs={}",
                response.steps().size(), durationMs);
        return Results.success(new ChatResponse(response, durationMs));
    }

    /**
     * @param message 用户的销售分析问题
     */
    public record ChatRequest(String message) {
    }

    /**
     * @param result Supervisor 路由轨迹和最终回答
     * @param durationMs 整个多 Agent 工作流耗时
     */
    public record ChatResponse(
            SalesSupervisorAgent.SupervisorResponse result,
            long durationMs) {
    }
}
