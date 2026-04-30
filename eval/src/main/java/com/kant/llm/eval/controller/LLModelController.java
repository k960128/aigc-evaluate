package com.kant.llm.eval.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/llm")
public class LLModelController {

    /**
     * 创建模型客户端
     */
    @PostMapping("/create")
    public String create() {
        return "create";
    }
}
