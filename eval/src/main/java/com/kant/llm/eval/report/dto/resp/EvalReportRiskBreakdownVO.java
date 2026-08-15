package com.kant.llm.eval.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 风险类别维度统计。
 *
 * <p>该对象按样本绑定的目标风险小类聚合结果，用于展示不同风险类别下的安全、违规、
 * 人工核验、失败和停止分布。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalReportRiskBreakdownVO {

    /** 风险大类 ID，对应 risk_category.id。 */
    private Long categoryId;

    /** 风险大类名称。 */
    private String categoryName;

    /** 风险小类 ID，对应 risk_details.id，也是样本绑定的目标风险小类。 */
    private Long riskDetailsId;

    /** 风险小类名称，优先取 risk_details.details_name，缺失时回退 dataset_sample.risk_details_name。 */
    private String riskDetailsName;

    /** 当前风险小类下的样本总数。 */
    private Long totalCount;

    /** 当前风险小类下明确判定安全的样本数。 */
    private Long safeCount;

    /** 当前风险小类下明确判定违规的样本数。 */
    private Long unsafeCount;

    /** 当前风险小类下需要人工核验的样本数。 */
    private Long manualReviewCount;

    /** 当前风险小类下执行失败的样本数。 */
    private Long failedCount;

    /** 当前风险小类下已终止的样本数。 */
    private Long stoppedCount;

    /** 当前风险小类安全率，口径为 safeCount / (safeCount + unsafeCount)。 */
    private BigDecimal safeRate;

    /** 当前风险小类风险率，口径为 unsafeCount / (safeCount + unsafeCount)。 */
    private BigDecimal riskRate;

    /** 当前风险小类人工核验率，口径为 manualReviewCount / (safeCount + unsafeCount + manualReviewCount)。 */
    private BigDecimal manualReviewRate;
}
