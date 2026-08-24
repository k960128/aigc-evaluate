package com.kant.llm.eval.knowledge.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语义知识库版本快照VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeFeatureDocumentVersionSnapshotVO {

    private Long versionId;

    /**
     * 版本号（语义化版本，如 1|2|3）
     */
    private Integer version;

    /**
     * 版本状态：UPLOADED, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED, STORED
     */
    private String status;
}
