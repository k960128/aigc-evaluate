package com.kant.llm.eval.agent.probe.dto.resp;

import com.kant.agent.contract.evidence.EvidenceContent;

/**
 * 经身份和归属校验后返回的原始证据。
 *
 * @param reference 证据索引与完整性摘要
 * @param content   原始提示、响应或错误正文；前端必须按纯文本展示
 */
public record EvidenceContentResponse(
        EvidenceReferenceResponse reference,
        String content) {

    /** 将领域证据正文转换为 API 响应。 */
    public static EvidenceContentResponse from(EvidenceContent evidence) {
        return new EvidenceContentResponse(
                EvidenceReferenceResponse.from(evidence.reference()),
                evidence.content());
    }
}
