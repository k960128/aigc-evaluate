package com.kant.llm.eval.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * L2 判定结果枚举。
 *
 * <p>L2 只负责基于召回证据、RRF、Reranker 和阈值做路由，
 * L3 暂未接入时，模糊区会进入人工核验。</p>
 */
@Getter
@AllArgsConstructor
public enum L2DecisionTypeEnums {

    /** 高置信违规短路，不再进入后续节点。 */
    UNSAFE("UNSAFE", "高置信违规"),

    /** 低风险安全短路，可以直接完成自动评分。 */
    SAFE("SAFE", "低风险安全"),

    /** 模糊区：理论上进入 L3，本阶段先进入人工核验。 */
    PASS_TO_L3("PASS_TO_L3", "进入L3或人工核验"),

    /** L2 没有可用外部召回能力时的降级安全判定。 */
    DEGRADED_SAFE("DEGRADED_SAFE", "降级安全");

    private final String code;

    private final String desc;
}
