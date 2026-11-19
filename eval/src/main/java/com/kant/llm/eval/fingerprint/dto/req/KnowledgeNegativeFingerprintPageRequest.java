package com.kant.llm.eval.fingerprint.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeNegativeFingerprintPageRequest {
    /** 所属风险明细 ID */
    private Long riskDetailsId;

    /** 字面量特征词 */
    private String keyword;

    /** 匹配模式: 1-精确匹配, 2-正则匹配 */
    private Integer matchType;

    /** 当前页码，默认值为 1。 */
    private Integer current;

    /** 每页数量，默认值为 10。 */
    private Integer size;
}
