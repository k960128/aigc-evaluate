package com.chinatelecom.aigc.evaluate.controller;

import com.chinatelecom.aigc.evaluate.common.pojo.CommonResult;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.TaskAnswerDO;
import com.chinatelecom.aigc.evaluate.dto.req.TaskAnswerPageReq;
import com.chinatelecom.aigc.evaluate.dto.resp.EvaluateResultStatisticsGroupResp;
import com.chinatelecom.aigc.evaluate.dto.resp.EvaluateResultStatisticsResp;
import com.chinatelecom.aigc.evaluate.dto.resp.TaskAnswerPageResp;
import com.chinatelecom.aigc.evaluate.service.TaskAnswerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.chinatelecom.aigc.evaluate.common.pojo.CommonResult.success;


@Slf4j
@RestController
@RequestMapping("/aigc/evaluate/task-answer")
@Api(tags = "任务结果管理 - TaskAnswer")
public class TaskAnswerController {

    private final TaskAnswerService taskAnswerService;

    public TaskAnswerController(TaskAnswerService taskAnswerService) {
        this.taskAnswerService = taskAnswerService;
    }
    @DeleteMapping("/delete")
    @ApiOperation("删除任务答案")
    public CommonResult<Void> delete(
            @ApiParam(value = "任务答案ID", required = true) @RequestParam("taskAnswerId") String taskAnswerId) {
        int row = taskAnswerService.delete(taskAnswerId);
        log.info("删除成功，受影响行数：{}", row);
        return CommonResult.success("删除成功", 0);
    }

    @PostMapping("/page")
    @ApiOperation("获得任务答案分页")
    public CommonResult<PageResult<TaskAnswerDO>> getTaskAnswerPage(
            @Valid TaskAnswerPageReq taskAnswerPageReq) {
        return CommonResult.success(taskAnswerService.getTaskAnswerPage(taskAnswerPageReq));
    }

    @PostMapping("/all-page")
    @ApiOperation("分模型获得任务答案分页")
    public CommonResult<PageResult<TaskAnswerPageResp>> getTaskAnswerAllPage(
            @Valid TaskAnswerPageReq taskAnswerPageReq) {
        return CommonResult.success(taskAnswerService.getTaskAnswerAllPage(taskAnswerPageReq));
    }

    @GetMapping("/get")
    @ApiOperation("根据任务答案ID查询数据")
    public CommonResult<TaskAnswerDO> get(
            @ApiParam(value = "任务答案ID", required = true) @RequestParam("taskAnswerId") String taskAnswerId) {
        return CommonResult.success(taskAnswerService.getByTaskAnswerId(taskAnswerId));
    }

    @PutMapping("/update-judge-result")
    @ApiOperation("更新任务答案的判卷结果")
    public CommonResult<Void> updateJudgeResult(
            @ApiParam(value = "任务答案ID", required = true) @RequestParam("id") Long id,
            @ApiParam(value = "判卷结果 0：未评判 1：回答正确 2：回答错误  3：无法评判", required = true) @RequestParam("judgeResult") Long judgeResult) {
        taskAnswerService.updateJudgeResultById(judgeResult, id);
        return CommonResult.success("修改成功", 0);
    }

    @GetMapping("/statistics")
    @ApiOperation("评测结果统计")
    public CommonResult<EvaluateResultStatisticsGroupResp> getTaskAnswerStatistics(
            @RequestParam("taskId") Long taskId,
            @RequestParam("modelId") Long modelId) {
        EvaluateResultStatisticsGroupResp stats = taskAnswerService.getEvaluateResultStatisticsByTaskIdAndModelId(taskId, modelId);

        // 判断 stats 是否为空，如果为空返回失败
        if (stats == null) {
            return CommonResult.error(CommonResult.error(-1, "统计结果未找到"));
        }

        return CommonResult.success(stats);
    }

    @GetMapping("/all-statistics")
    @ApiOperation("所有模型评测结果统计")
    public CommonResult<Map<Long, EvaluateResultStatisticsGroupResp>> getTaskAnswerAllStatistics(
            @RequestParam Long taskId) {
        Map<Long, EvaluateResultStatisticsGroupResp> stats = taskAnswerService.getEvaluateResultStatisticsByTaskId(taskId);
        return CommonResult.success(stats);
    }

    @PutMapping("/auto-judgment")
    @ApiOperation("异步自动判卷")
    public CommonResult<Void> autoJudgment(
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") Long taskId,
            @ApiParam(value = "模型ID", required = true) @RequestParam("modelId") Long modelId) {
        if (!taskAnswerService.autoJudgment(taskId, modelId)) {
            return CommonResult.success("正在自动评判中....", 0);
        }

        return CommonResult.success("评判任务已下发，正在处理", 0);
    }

    @PutMapping("/sync-judgment")
    @ApiOperation("同步机审结果到人审")
    public CommonResult<Void> syncJudgmentToManualReview(
            @ApiParam(value = "任务ID", required = true) @RequestParam("taskId") Long taskId,
            @ApiParam(value = "模型ID（可选）") @RequestParam(value = "modelId", required = false) Long modelId) {

        // 调用同步方法
        boolean success = taskAnswerService.syncMachineJudgmentToManualReview(taskId, modelId);

        if (success) {
            return CommonResult.success("同步成功", 0);
        } else {
            return CommonResult.success("没有未评判的记录，无需同步", 0);
        }
    }

}
