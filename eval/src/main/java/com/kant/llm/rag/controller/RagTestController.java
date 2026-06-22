package com.kant.llm.rag.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * RAG模块 测试控制器
 */
@RestController
@RequestMapping("/rag/test")
public class RagTestController {


    @PostMapping(value = "/chat-stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(){
        // 每秒推送一条消息，持续输出
        return Flux.interval(Duration.ofSeconds(1))
                .map(i -> "实时消息：" + i)
                .take(10); // 推送10条结束
    }

    @GetMapping("/chat-mono")
    public Mono<String> chatMono(){
        return Mono.just("hello world");
    }

    /**
     * 聊天接口
     * @return
     */
    public Result<String> chat(){
        return Results.success("hello world");
    }
}
