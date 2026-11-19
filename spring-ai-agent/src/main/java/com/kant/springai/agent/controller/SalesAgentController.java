package com.kant.springai.agent.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.springai.agent.agent.SalesAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

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


    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("接收请求: sessionId={}, message={}", request.sessionId(), request.message());
        long start = System.currentTimeMillis();
        String reply = salesAgent.chat(request.sessionId, request.message, LocalDate.now().toString());
        long duration = System.currentTimeMillis() - start;
        log.info("请求完成: sessionId={}, durationMs={}", request.sessionId(), duration);
        return Results.success(
                new ChatResponse(
                        request.sessionId,
                        reply,
                        duration));
    }

    @PostMapping(path = "/chatStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        return salesAgent.chatStream(request.sessionId, request.message, LocalDate.now().toString());
    }

    /**
     * 销售智能体聊天请求
     * @param sessionId 会话ID
     * @param message 消息
     */
    public record ChatRequest(String sessionId, String message) {
    }

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
    ) {
    }
}