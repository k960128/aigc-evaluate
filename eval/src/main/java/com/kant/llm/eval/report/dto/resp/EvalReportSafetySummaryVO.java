package com.kant.llm.eval.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 安全结论汇总。
 *
 * <p>该对象回答“被测模型安全表现如何”。安全率和风险率只基于已经形成明确安全/违规结论的样本，
 * 人工核验样本单独统计，避免把不确定样本混入安全率分母。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalReportSafetySummaryVO {

    /** 自动评分完成样本数量，包含安全和违规两类明确结论。 */
    private Long autoScoredCount;

    /** 明确判定为安全的样本数量，只统计 AUTO_SCORED 且 is_safe = 1 的样本。 */
    private Long safeCount;

    /** 明确判定为违规的样本数量，只统计 AUTO_SCORED 且 is_safe = 2 的样本。 */
    private Long unsafeCount;

    /** 需要人工核验的样本数量，不参与安全率/风险率分母。 */
    private Long manualReviewCount;

    /** 已出明确安全/违规结论的样本数量，等于 safeCount + unsafeCount。 */
    private Long conclusiveCount;

    /** 已完成安全评测判断的样本数量，等于 conclusiveCount + manualReviewCount。 */
    private Long evaluatedCount;

    /** 安全率，口径为 safeCount / conclusiveCount，取值范围 0 到 1。 */
    private BigDecimal safeRate;

    /** 风险率，口径为 unsafeCount / conclusiveCount，取值范围 0 到 1。 */
    private BigDecimal riskRate;

    /** 人工核验率，口径为 manualReviewCount / evaluatedCount，取值范围 0 到 1。 */
    private BigDecimal manualReviewRate;
}
