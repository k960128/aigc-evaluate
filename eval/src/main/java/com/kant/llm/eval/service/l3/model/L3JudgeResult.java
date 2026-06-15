package com.kant.llm.eval.service.l3.model;

import com.kant.llm.eval.common.enums.L3DecisionTypeEnums;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * L3 Judge 客户端返回。
 *
 * <p>真实 Judge 模型接入后，应把模型 JSON 解析为该结构；默认降级实现也返回该结构。</p>
 */
@Data
@Builder
public class L3JudgeResult {

    /** Judge 决策。 */
    private L3DecisionTypeEnums decision;

    /** Judge 置信度，范围 0-1。 */
    private BigDecimal confidence;

    /** Judge 判定对应的风险小类 ID。 */
    private Long riskDetailsId;

    /** 判定原因。 */
    private String reason;

    /** 支撑判定的证据片段。 */
    private List<String> evidence;

    /** Judge 原始返回内容。 */
    private String rawResponse;

    /** 是否为降级结果。 */
    private Boolean degraded;
}
