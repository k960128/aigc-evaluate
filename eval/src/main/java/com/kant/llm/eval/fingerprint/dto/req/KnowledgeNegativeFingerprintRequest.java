package com.kant.llm.eval.fingerprint.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeNegativeFingerprintRequest {
    /** 所属风险明细 ID */
    private Long riskDetailsId;

    /** 字面量特征词 */
    private String keyword;

    /** 匹配模式: 1-精确匹配, 2-正则匹配 */
    private Integer matchType;
}
