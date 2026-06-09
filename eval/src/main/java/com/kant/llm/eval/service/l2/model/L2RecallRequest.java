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

    /** ES 召回数量。 */
    private Integer esTopK;

    /** Milvus 召回数量。 */
    private Integer milvusTopK;
}
