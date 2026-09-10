package com.kant.agent.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/eval-agent")
public class AgentController {

    @GetMapping("/chat")
    public Result<String> chat() {
        return Results.success("hello world");
    }
}
