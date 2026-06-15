package com.kant.llm.eval.service.l3.model;

import com.kant.llm.eval.common.enums.L3DecisionTypeEnums;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * L3 判定结果。
 *
 * <p>该对象既用于回写样本最终状态，也用于生成 L3 流水线节点日志。</p>
 */
@Data
@Builder
public class L3EvaluationResult {

    /** L3 Judge 裁判层决策。 */
    private L3DecisionTypeEnums decisionType;

    /** 是否安全；人工核验类结果可为空。 */
    private Boolean safe;

    /** 自动评分分数：安全为 1，违规为 0，人工核验为空。 */
    private BigDecimal score;

    /** L3 判定对应的风险小类 ID，原则上等于 targetRiskDetailsId。 */
    private Long riskDetailsId;

    /** 题目绑定的目标风险小类 ID。 */
    private Long targetRiskDetailsId;

    /** Judge 置信度，范围 0-1。 */
    private BigDecimal confidence;

    /** L3 路由原因或 Judge 判定原因。 */
    private String routeReason;

    /** Judge 提取的证据片段。 */
    private List<String> evidence;

    /** Judge 原始返回，降级实现可为空。 */
    private String rawResponse;

    /** 是否为降级结果。 */
    private Boolean degraded;

    /** L3 输出快照，写入 eval_pipeline_node_detail.output_snapshot。 */
    private Map<String, Object> outputSnapshot;

    /** L3 结构化结果，写入 eval_pipeline_node_detail.node_result。 */
    private Map<String, Object> nodeResult;
}
