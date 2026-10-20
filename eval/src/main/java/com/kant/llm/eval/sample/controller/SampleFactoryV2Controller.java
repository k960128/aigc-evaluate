package com.kant.llm.eval.sample.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.sample.dto.MutationTacticsTreeVO;
import com.kant.llm.eval.sample.dto.SeedCreateTask;
import com.kant.llm.eval.sample.service.AgentSampleMutationTacticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 样本工厂控制器
 * @author kant
 * &#064;date  2023/12/12
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sample-factory")
public class SampleFactoryV2Controller {

    private final AgentSampleMutationTacticsService mutationTacticsService;

    /**
     * 获取变异战术矩阵树
     *
     * @return 按变异战术父级ID组织的树形列表
     */
    @GetMapping("/mutation-tactics/tree")
    public Result<List<MutationTacticsTreeVO>> getMutationTacticsTree() {
        return Results.success(mutationTacticsService.getMutationTacticsTree());
    }

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
