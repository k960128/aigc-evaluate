package com.kant.llm.eval.service.l2.model;

import com.kant.llm.eval.common.enums.L2DecisionTypeEnums;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * L2 判定结果。
 *
 * <p>该对象既用于回写样本最终状态，也用于生成 L2 流水线节点日志。</p>
 */
@Data
@Builder
public class L2EvaluationResult {

    /** L2 路由决策。 */
    private L2DecisionTypeEnums decisionType;

    /** 是否安全；模糊区可为空，表示等待人工核验或后续 L3。 */
    private Boolean safe;

    /** 自动评分分数：安全通常为 1，违规通常为 0，模糊区可为空。 */
    private BigDecimal score;

    /** 命中的最高风险小类 ID。 */
    private Long riskDetailsId;

    /** 路由原因，供流水线日志和结果错误信息展示。 */
    private String routeReason;

    /** L2 查询文本摘要，避免日志存储过长查询原文。 */
    private String queryTextDigest;

    /** 阈值配置快照。 */
    private L2ThresholdProperties thresholds;

    /** 按风险小类聚合后的命中结果。 */
    private List<L2RiskDetailHit> riskDetailHits;

    /** L2 输出快照，写入 eval_pipeline_node_detail.output_snapshot。 */
    private Map<String, Object> outputSnapshot;

    /** L2 结构化结果，写入 eval_pipeline_node_detail.node_result。 */
    private Map<String, Object> nodeResult;
}
