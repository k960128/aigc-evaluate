package com.kant.llm.eval.service.l2.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * L2 Reranker 精排请求。
 */
@Data
@Builder
public class L2RerankRequest {

    /** 当前模型输出和用户输入拼接后的查询文本。 */
    private String queryText;

    /** RRF 后进入精排的候选特征。 */
    private List<L2FeatureHit> candidates;
}
