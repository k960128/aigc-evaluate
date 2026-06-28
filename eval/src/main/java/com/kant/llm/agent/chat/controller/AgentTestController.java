package com.kant.llm.agent.chat.controller;

import com.alibaba.fastjson.JSON;
import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.agent.ai.model.IntentRecognitionResult;
import com.kant.llm.agent.ai.service.IntentRecognitionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/test")
public class AgentTestController {


    private final IntentRecognitionService intentRecognitionService;

    public AgentTestController(IntentRecognitionService intentRecognitionService) {
        this.intentRecognitionService = intentRecognitionService;
    }

    /**
     * 测试意图识别服务
     *
     * @param query query
     * @return resp
     */
    @GetMapping("/testInterRecognition")
    public Result<IntentRecognitionResult> testInterRecognition(@RequestParam("query") String query) {
        return Results.success(intentRecognitionService.chat(query));
    }
}
