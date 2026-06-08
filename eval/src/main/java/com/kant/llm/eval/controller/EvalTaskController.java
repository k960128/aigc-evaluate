package com.kant.llm.eval.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.llm.eval.common.convention.Result;
import com.kant.llm.eval.common.web.Results;
import com.kant.llm.eval.dto.req.CreateEvalTaskRequest;
import com.kant.llm.eval.dto.req.EvalResultDetailPageRequest;
import com.kant.llm.eval.dto.req.EvalTaskDetailPageRequest;
import com.kant.llm.eval.dto.req.EvalTaskPageRequest;
import com.kant.llm.eval.dto.resp.EvalPipelineNodeDetailVO;
import com.kant.llm.eval.dto.resp.EvalResultDetailVO;
import com.kant.llm.eval.dto.resp.EvalTaskDetailVO;
import com.kant.llm.eval.dto.resp.EvalTaskStatusVO;
import com.kant.llm.eval.dto.resp.EvalTaskVO;
import com.kant.llm.eval.service.EvalTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/eval-task")
public class EvalTaskController {

    private final EvalTaskService evalTaskService;

    public EvalTaskController(EvalTaskService evalTaskService) {
        this.evalTaskService = evalTaskService;
    }

    /**
     * 创建评测任务
     * @param request 创建评测任务请求参数
     * @return 无返回数据
     */
    @PostMapping("/create")
    public void createEvalTask(@RequestBody CreateEvalTaskRequest request) {
        evalTaskService.createEvalTask(request);
    }

    /**
     * 发起评测任务
     * @param taskId 评测任务ID
     * @return 无返回数据
     */
    @PostMapping("/submit")
    public Result<Boolean> submitEvalTask(@RequestParam("taskId") Long taskId) {
        Boolean b = evalTaskService.submitEvalTask(taskId);
        return Results.success(b);
    }

    /**
     * 终止评测任务当前批次
     *
     * <p>停止后会保留已完成样本结果，未开始样本不再执行；如需重新评测，需要重新提交任务。</p>
     *
     * @param taskId 评测任务ID
     * @return 停止请求处理结果
     */
    @PostMapping("/stop")
    public Result<Boolean> stopEvalTask(@RequestParam("taskId") Long taskId) {
        Boolean stopped = evalTaskService.stopEvalTask(taskId);
        return Results.success(stopped);
    }

    /**
     * 分页查询评测任务列表。
     *
     * <p>返回任务定义信息，并补充最近一次执行批次的状态和进度摘要。</p>
     *
     * @param request 任务分页查询请求参数
     * @return 评测任务分页结果
     */
    @PostMapping("/page")
    public Result<Page<EvalTaskVO>> pageEvalTask(@RequestBody EvalTaskPageRequest request) {
        Page<EvalTaskVO> pageResult = evalTaskService.pageEvalTask(request);
        return Results.success(pageResult);
    }

    /**
     * 分页查询评测任务执行批次列表。
     *
     * <p>同一个评测任务每次提交都会产生一个执行批次，该接口用于查看历史批次。</p>
     *
     * @param request 执行批次分页查询请求参数
     * @return 执行批次分页结果
     */
    @PostMapping("/detail/page")
    public Result<Page<EvalTaskDetailVO>> pageEvalTaskDetail(@RequestBody EvalTaskDetailPageRequest request) {
        Page<EvalTaskDetailVO> pageResult = evalTaskService.pageEvalTaskDetail(request);
        return Results.success(pageResult);
    }

    /**
     * 分页查询评测结果明细列表。
     *
     * <p>用于查看某个任务或某次执行批次下的单样本评测结果，支持按状态、安全性、样本和输入关键字筛选。</p>
     *
     * @param request 评测结果明细分页查询请求参数
     * @return 评测结果明细分页结果
     */
    @PostMapping("/result/page")
    public Result<Page<EvalResultDetailVO>> pageEvalResultDetail(@RequestBody EvalResultDetailPageRequest request) {
        Page<EvalResultDetailVO> pageResult = evalTaskService.pageEvalResultDetail(request);
        return Results.success(pageResult);
    }

    /**
     * 查询评测任务状态。
     *
     * <p>优先返回当前活跃批次；没有活跃批次时返回最近一次批次；从未提交时返回未提交语义。</p>
     *
     * @param taskId 评测任务 ID
     * @return 任务状态信息
     */
    @GetMapping("/status")
    public Result<EvalTaskStatusVO> getEvalTaskStatus(@RequestParam("taskId") Long taskId) {
        EvalTaskStatusVO status = evalTaskService.getEvalTaskStatus(taskId);
        return Results.success(status);
    }

    /**
     * 查询单条样本结果的流水线节点日志。
     *
     * <p>按节点开始时间、创建时间和节点日志 ID 正序返回，便于前端还原执行链路。</p>
     *
     * @param resultDetailId 评测结果明细 ID
     * @return 流水线节点日志列表
     */
    @GetMapping("/pipeline-node/list")
    public Result<List<EvalPipelineNodeDetailVO>> listPipelineNodeLogs(@RequestParam("resultDetailId") Long resultDetailId) {
        List<EvalPipelineNodeDetailVO> logs = evalTaskService.listPipelineNodeLogs(resultDetailId);
        return Results.success(logs);
    }

    /**
     * 获取评测任务进度
     * @param taskId 评测任务ID
     * @return 无返回数据
     */
    @GetMapping("/progress")
    public void getEvalTaskProgress(@RequestParam("taskId") Long taskId) {
//        evalTaskService.getEvalTaskProgress(taskId);
    }
}
