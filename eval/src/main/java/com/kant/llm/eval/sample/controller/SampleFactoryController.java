package com.kant.llm.eval.sample.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.sample.dto.SeedCreateTask;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 样本工厂控制器
 * @author kant
 * &#064;date  2023/12/12
 */
@Slf4j
@RestController
@RequestMapping("/sample-factory")
public class SampleFactoryController {

    /**
     * 创建样本扩充任务
     * @param request 样本创建任务请求
     * @return 样本创建任务响应
     */
    @PostMapping("/tasks")
    public Result<Boolean> createSampleFactoryTask(@Valid @RequestBody SeedCreateTask request) {
        return Results.success(true);
    }
}
