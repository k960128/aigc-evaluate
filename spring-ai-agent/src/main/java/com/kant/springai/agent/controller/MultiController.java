package com.kant.springai.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.springai.agent.dto.ArticleResult;
import com.kant.springai.agent.dto.GenerateRequest;
import com.kant.springai.agent.service.ContentOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * 多智能体
 */
@Slf4j
@RestController
@RequestMapping("/content")
public class MultiController {
    private final ContentOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public MultiController(ContentOrchestrator orchestrator,
                           ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    /** 同步接口：一次请求拿到最终结果 */
    @PostMapping("/generate")
    public ArticleResult generate(@RequestBody GenerateRequest request) {
        return orchestrator.produce(request.topic());
    }

    /**
     * 关键点：
     * 1. produces 显式带 charset=UTF-8
     * 2. send 时指定 MediaType
     * 3. 用 JSON 包装消息，避免手工拼字符串
     */
    @GetMapping(value = "/generate/stream",
            produces = "text/event-stream;charset=UTF-8")
    public SseEmitter generateStream(@RequestParam String topic) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        emitter.onTimeout(() -> emitter.complete());
        emitter.onError(e -> emitter.complete());

        Thread worker = new Thread(() -> {
            try {
                ArticleResult result = orchestrator.produce(topic, msg -> {
                    try {
                        String payload = objectMapper.writeValueAsString(
                                Map.of("message", msg, "ts", System.currentTimeMillis()));
                        emitter.send(SseEmitter.event()
                                .name("progress")
                                .data(payload, MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });

                String resultJson = objectMapper.writeValueAsString(result);
                emitter.send(SseEmitter.event()
                        .name("result")
                        .data(resultJson, MediaType.APPLICATION_JSON));

                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }, "multi-agent-worker");

        worker.setDaemon(true);
        worker.start();
        return emitter;
    }


}
