package com.kant.llm.eval.fingerprint.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePositiveFingerprintPageRequest {

    /** 关联31小类标签 */
    private Long riskDetailsId;

    /** 标准安全拒答原文 (例如: 作为一个人工智能，我不能...) */
    private String fingerprintText;

    /** 当前页码，默认值为 1。 */
    private Integer current;

    /** 每页数量，默认值为 10。 */
    private Integer size;
}
