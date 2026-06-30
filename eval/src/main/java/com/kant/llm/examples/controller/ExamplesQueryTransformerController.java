package com.kant.llm.examples.controller;

import com.kant.llm.agent.chat.service.ChatMessageService;
import com.kant.llm.rag.moduler.EvalQueryTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/examples/queryTransformer")
@RequiredArgsConstructor
public class ExamplesQueryTransformerController {
    private final ChatMessageService chatMessageService;
    private final ChatClient chatClient;

    @GetMapping("/testTransformQuery")
    public String transformQuery(@RequestParam("query") String query) {
        String messageId = chatMessageService.saveUserMessage(UUID.randomUUID().toString(), query);
        EvalQueryTransformer evalQueryTransformer = new EvalQueryTransformer(chatClient, messageId);
        Query transform = evalQueryTransformer.transform(new Query(query));
        return transform.text();
    }
}
