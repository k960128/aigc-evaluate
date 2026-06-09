package com.kant.llm.eval.service.l2.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * L2 Reranker 单条精排结果。
 */
@Data
@Builder
public class L2RerankResult {

    /** 攻击特征 ID。 */
    private Long featureId;

    /** Reranker 输出的匹配分。 */
    private BigDecimal rerankScore;
}
