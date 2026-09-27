package com.kant.springai.agent.controller;

import com.kant.springai.agent.agent.SalesAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售智能体控制器
 * @author kant
 * @date 2023/12/12
 */
@Slf4j
@RestController
@RequestMapping("/sales/agent")
public class SalesAgentController {
    private final SalesAgent salesAgent;

    public SalesAgentController(SalesAgent salesAgent) {
        this.salesAgent = salesAgent;
    }


    /**
     * 销售智能体聊天请求
     * @param sessionId 会话ID
     * @param message 消息
     */
    public record ChatRequest(String sessionId,String message){}

    /**
     * 销售智能体聊天响应
     * @param sessionId 会话ID
     * @param reply 回复
     * @param durationMs 耗时
     */
    public record ChatResponse(
            String sessionId,
            String reply,
            long durationMs
    ) {}
}
