package com.kant.llm.eval.knowledge.dto.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeFeatureVersionReq {
    /**
     * 风险小类ID
     */
    private Long riskDetailsId;
}
