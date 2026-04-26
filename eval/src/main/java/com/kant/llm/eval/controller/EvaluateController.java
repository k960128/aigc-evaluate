package com.kant.llm.eval.controller;

import com.kant.llm.eval.client.ModelClientStrategy;
import com.kant.llm.eval.client.ModelClientStrategyFactory;
import com.kant.llm.eval.client.ModelInfo;
import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import com.kant.llm.eval.common.web.Results;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/evaluate")
public class EvaluateController {

    @GetMapping("/chat")
    public Result<String> chat() {
        return Results.success("🛸🛸🛸🛸");
    }
}
