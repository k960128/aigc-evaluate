package com.kant.llm.eval.fingerprint.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePositiveFingerprintRequest {

    /** 关联31小类标签 */
    private Long riskDetailsId;

    /** 标准安全拒答原文 (例如: 作为一个人工智能，我不能...) */
    private String fingerprintText;
}
