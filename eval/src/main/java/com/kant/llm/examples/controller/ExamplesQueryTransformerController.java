package com.kant.llm.examples.controller;

import com.alibaba.fastjson.JSON;
import com.kant.llm.agent.ai.service.QueryTransformerService;
import com.kant.llm.agent.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final QueryTransformerService queryTransformerService;
    private final ChatMessageService chatMessageService;

    @GetMapping("/testTransformQuery")
    public String transformQuery(@RequestParam("query") String query) {
        String messageId = chatMessageService.saveUserMessage(UUID.randomUUID().toString(), query);
        return JSON.toJSONString(queryTransformerService.transformer(query,messageId,null));
    }


}
