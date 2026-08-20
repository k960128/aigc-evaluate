package com.kant.llm.eval.knowledge.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 攻击语义知识库版本响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeFeatureDocumentVersionVO {
    /**
     * 状态：INIT, UPLOADED, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED
     */
    private String status;

    /**
     * 当前激活版本ID，指向 knowledge_attack_feature_document_version_snapshot.version_id
     */
    private Long currentVersionId;

    /**
     * 风险详情ID
     */
    private Long riskDetailsId;
}
