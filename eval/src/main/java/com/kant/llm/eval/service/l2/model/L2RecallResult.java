package com.kant.llm.eval.service.l2.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * L2 双路召回结果。
 */
@Data
@Builder
public class L2RecallResult {

    /** ES 字面召回命中列表。 */
    private List<L2FeatureHit> esHits;

    /** Milvus 语义召回命中列表。 */
    private List<L2FeatureHit> milvusHits;

    /** 是否为降级召回结果；true 表示外部召回暂不可用。 */
    private Boolean degraded;
}
