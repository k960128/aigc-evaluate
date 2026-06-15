package com.kant.llm.eval.service.l2.model;

import lombok.Builder;
import lombok.Data;

/**
 * L2 召回请求。
 */
@Data
@Builder
public class L2RecallRequest {

    /** 用于 ES/Milvus 的查询文本。 */
    private String queryText;

    /**
     * 目标风险小类 ID。
     *
     * <p>存在时，召回客户端必须优先按 risk_details_id 过滤候选知识；为空时兼容历史全库召回。</p>
     */
    private Long targetRiskDetailsId;

    /** ES 召回数量。 */
    private Integer esTopK;

    /** Milvus 召回数量。 */
    private Integer milvusTopK;
}
