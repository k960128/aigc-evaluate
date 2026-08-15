package com.kant.llm.eval.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 执行质量汇总。
 *
 * <p>该对象回答“这次批次跑得怎么样”：多少样本还在等待、多少已经自动评分、
 * 多少进入人工核验、多少失败或停止。它不表达模型是否安全，安全结论由 safety 字段承载。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalReportExecutionSummaryVO {

    /** 批次样本总数，优先取 eval_task_detail.total_count，缺失时回退为结果明细数量。 */
    private Integer totalCount;

    /** 批次已推进完成的样本数，沿用 eval_task_detail.finished_count。 */
    private Integer finishedCount;

    /** 当前批次下已生成的结果明细总数。 */
    private Long resultCount;

    /** 未处理样本数量，对应 EvalResultStatusEnums.PENDING。 */
    private Long pendingCount;

    /** 模型调用中样本数量，对应 EvalResultStatusEnums.MODEL_RUNNING。 */
    private Long modelRunningCount;

    /** 模型调用完成、等待安全评测的样本数量，对应 EvalResultStatusEnums.MODEL_CALLED。 */
    private Long modelCalledCount;

    /** L1/L2/L3 安全评测中的样本数量，对应 EvalResultStatusEnums.SAFETY_EVALUATING。 */
    private Long safetyEvaluatingCount;

    /** 自动评分完成样本数量，对应 EvalResultStatusEnums.AUTO_SCORED。 */
    private Long autoScoredCount;

    /** 需要人工核验的样本数量，对应 EvalResultStatusEnums.MANUAL_REVIEWED。 */
    private Long manualReviewCount;

    /** 执行失败样本数量，对应 EvalResultStatusEnums.FAILED。 */
    private Long failedCount;

    /** 已终止样本数量，对应 EvalResultStatusEnums.STOPPED。 */
    private Long stoppedCount;

    /** 批次进度百分比，按 finishedCount / totalCount 计算，范围 0 到 100。 */
    private Integer progressPercent;

    /** 执行成功率，口径为 (AUTO_SCORED + MANUAL_REVIEWED) / totalCount。 */
    private BigDecimal executionSuccessRate;

    /** 执行失败率，口径为 FAILED / totalCount。 */
    private BigDecimal failedRate;

    /** 终止率，口径为 STOPPED / totalCount。 */
    private BigDecimal stoppedRate;

    /** 结果状态计数字典，key 为 EvalResultStatusEnums.code，value 为该状态样本数量。 */
    private Map<Integer, Long> statusCounts;
}
