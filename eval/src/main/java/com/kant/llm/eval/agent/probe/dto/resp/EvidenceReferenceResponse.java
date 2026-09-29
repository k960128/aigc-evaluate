package com.kant.llm.eval.agent.probe.dto.resp;

import com.kant.agent.contract.evidence.EvidenceReference;

import java.time.Instant;

/**
 * 不包含原始正文的证据引用响应。
 *
 * @param evidenceRef 证据唯一引用
 * @param type        证据类型
 * @param ownerType   所属业务对象类型
 * @param ownerRef    所属物理执行标识
 * @param mediaType   内容媒体类型
 * @param contentHash 原始正文 SHA-256 摘要
 * @param dataClass   数据分级
 * @param createdAt   证据创建时间
 */
public record EvidenceReferenceResponse(
        String evidenceRef,
        String type,
        String ownerType,
        String ownerRef,
        String mediaType,
        String contentHash,
        String dataClass,
        Instant createdAt) {

    /** 将领域证据索引转换为 API 响应。 */
    public static EvidenceReferenceResponse from(EvidenceReference reference) {
        if (reference == null) {
            return null;
        }
        return new EvidenceReferenceResponse(
                reference.evidenceRef(),
                reference.type().name(),
                reference.ownerType(),
                reference.ownerRef(),
                reference.mediaType(),
                reference.contentHash(),
                reference.dataClass().name(),
                reference.createdAt());
    }
}
