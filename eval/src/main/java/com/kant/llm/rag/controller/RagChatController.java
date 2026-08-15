package com.kant.llm.rag.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/rag")
public class RagChatController {

    /**
     * 发起 SSE 流式对话
     */
    @GetMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chat() {
        return Flux.just("hello world");
    }
}
