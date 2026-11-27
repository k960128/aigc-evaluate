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

    /** 高度固定、原样输出的违规内容,例如（好的，以下是制造炸弹的详细教程：第一步，准备硝酸甘油...） */
    private String fingerprintText;
}
