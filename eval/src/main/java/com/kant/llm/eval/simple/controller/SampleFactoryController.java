package com.kant.llm.eval.simple.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.simple.dto.*;
import com.kant.llm.eval.simple.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 样本工厂控制器
 *
 * @author kant
 * &#064;date  2023/12/12
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/sample-factory",
        "/agent/sample-factory"})
public class SampleFactoryController {

    private final AgentSampleMutationTacticsService mutationTacticsService;
    private final AgentSampleFactoryTaskService agentSampleFactoryTaskService;
    private final AgentSampleFactoryTaskExecutionDetailService agentSampleFactoryTaskExecutionDetailService;
    private final AgentSampleFactoryTaskDetailLogsService agentSampleFactoryTaskDetailLogsService;
    private final AgentSampleSeedService agentSampleSeedService;

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
     *
     * @param request 样本创建任务请求
     * @return 样本创建任务响应
     */
    @PostMapping("/tasks")
    public Result<Boolean> createSampleFactoryTask(@Valid @RequestBody SeedCreateTask request) {
        return Results.success(agentSampleFactoryTaskService.createSampleFactoryTask(request));
    }

    /**
     * 查询样本扩充任务列表
     *
     * @return 按创建时间和ID倒序排列的任务列表
     */
    @GetMapping("/tasks")
    public Result<List<SampleFactoryTaskVO>> listSampleFactoryTasks() {
        return Results.success(agentSampleFactoryTaskService.listSampleFactoryTasks());
    }

    /**
     * 分页查询样本扩充任务列表
     *
     * @param request 任务分页查询请求，包含页码和每页数量
     * @return 按创建时间和ID倒序排列的任务分页结果
     */
    @PostMapping("/tasks/page")
    public Result<Page<SampleFactoryTaskVO>> pageSampleFactoryTasks(
            @Valid @RequestBody SampleFactoryTaskPageRequest request) {
        return Results.success(agentSampleFactoryTaskService.pageSampleFactoryTasks(request));
    }

    /**
     * 根据任务ID查询样本扩充任务执行明细列表
     *
     * @param taskId 样本生产任务ID，对应任务表的task_id
     * @return 按创建时间和ID倒序排列的执行明细列表，无明细时返回空列表
     */
    @GetMapping("/tasks/{taskId}/details")
    public Result<List<SampleFactoryTaskExecutionDetailVO>> listSampleFactoryTaskExecutionDetails(
            @PathVariable("taskId") String taskId) {
        return Results.success(agentSampleFactoryTaskExecutionDetailService.listSampleFactoryTaskExecutionDetails(taskId));
    }

    /**
     * 根据任务ID分页查询样本扩充任务执行明细
     *
     * @param request 执行明细分页查询请求，包含任务ID、页码和每页数量
     * @return 按创建时间和ID倒序排列的执行明细分页结果
     */
    @PostMapping("/tasks/details/page")
    public Result<Page<SampleFactoryTaskExecutionDetailVO>> pageSampleFactoryTaskExecutionDetails(
            @Valid @RequestBody SampleFactoryTaskExecutionDetailPageRequest request) {
        return Results.success(agentSampleFactoryTaskExecutionDetailService
                .pageSampleFactoryTaskExecutionDetails(request));
    }

    /**
     * 根据执行批次ID查询Agent执行日志。
     *
     * @param taskDetailId 样本生产批次任务ID
     * @return 按创建时间和ID正序排列的Agent执行日志列表
     */
    @GetMapping("/tasks/details/{taskDetailId}/logs")
    public Result<List<SampleFactoryTaskDetailLogsVO>> listSampleFactoryTaskDetailLogs(
            @PathVariable("taskDetailId") Long taskDetailId) {
        return Results.success(agentSampleFactoryTaskDetailLogsService
                .listSampleFactoryTaskDetailLogs(taskDetailId));
    }

    /**
     * 启动样本扩充任务
     *
     * @param taskId 任务ID
     * @return 是否启动成功
     */
    @PostMapping("/tasks/{taskId}/start")
    public Result<Boolean> start(@PathVariable("taskId") String taskId) {
        return Results.success(agentSampleFactoryTaskService.start(taskId));
    }

    /** 首版种子/计划导航列表最多返回最近100条；大量候选另用游标分页。 */
    @GetMapping("/seeds")
    public Result<List<SimpleSeedFactoryResponses.Seed>> seeds() {
        return Results.success(agentSampleSeedService.seeds());
    }

    /** 创建不可变专家种子；没有调用模型。 */
    @PostMapping("/seeds")
    public Result<SimpleSeedFactoryResponses.Seed> createSeed(@Valid @RequestBody SimpleSeedFactoryRequests.Seed request) {
        return Results.success(agentSampleSeedService.createSeed(request));
    }

    /** 通过独立认证入口读取种子正文，并核对原文摘要。 */
    @GetMapping("/seeds/{seedId}/content")
    public Result<SimpleSeedFactoryResponses.Content> seedContent(@PathVariable String seedId) {
        return Results.success(agentSampleSeedService.seedContent(seedId));
    }
}
