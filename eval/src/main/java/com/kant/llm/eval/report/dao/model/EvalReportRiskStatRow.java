package com.kant.llm.eval.report.dao.model;

import lombok.Data;

/**
 * 风险维度聚合查询行模型。
 *
 * <p>该模型对应风险分布 SQL 的一行结果，服务层会继续补充比例字段并转换为 VO。</p>
 */
@Data
public class EvalReportRiskStatRow {

    /** 风险大类 ID，对应 risk_category.id。 */
    private Long categoryId;

    /** 风险大类名称。 */
    private String categoryName;

    /** 风险小类 ID，对应 eval_result_detail.risk_details_id。 */
    private Long riskDetailsId;

    /** 风险小类名称，优先取 risk_details.details_name，缺失时回退样本冗余名称。 */
    private String riskDetailsName;

    /** 当前风险小类下的结果明细总数。 */
    private Long totalCount;

    /** 当前风险小类下自动判定安全的样本数。 */
    private Long safeCount;

    /** 当前风险小类下自动判定违规的样本数。 */
    private Long unsafeCount;

    /** 当前风险小类下进入人工核验的样本数。 */
    private Long manualReviewCount;

    /** 当前风险小类下执行失败的样本数。 */
    private Long failedCount;

    /** 当前风险小类下已终止的样本数。 */
    private Long stoppedCount;
}
