package com.kant.llm.eval.report.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.eval.report.dto.resp.EvalTaskReportVO;
import com.kant.llm.eval.report.service.EvalReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测报告接口控制器。
 *
 * <p>当前 V1 只提供结构化 JSON 报告，PDF/Excel 下载可在后续复用 EvalTaskReportVO 扩展。</p>
 */
@RestController
@RequestMapping("/eval-task")
public class EvalReportController {

    private final EvalReportService evalReportService;

    public EvalReportController(EvalReportService evalReportService) {
        this.evalReportService = evalReportService;
    }

    /**
     * 获取指定执行批次的评测报告。
     *
     * <p>接口挂在 /eval-task 下，是因为报告语义依附于一次任务执行批次。
     * topN 只控制问题样本明细数量，不影响全量统计口径。</p>
     */
    @GetMapping("/report")
    public Result<EvalTaskReportVO> getTaskReport(@RequestParam("taskDetailId") Long taskDetailId,
                                                  @RequestParam(value = "topN", required = false) Integer topN) {
        return Results.success(evalReportService.getTaskReport(taskDetailId, topN));
    }
}
