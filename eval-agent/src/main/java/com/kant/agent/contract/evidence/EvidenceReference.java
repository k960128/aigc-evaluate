package com.kant.agent.contract.evidence;

import com.kant.agent.contract.common.ContractChecks;

import java.time.Instant;

/**
 * 证据索引，不直接携带原始敏感内容。
 *
 * @param evidenceRef 证据唯一引用
 * @param type        证据类型
 * @param ownerType   证据所属业务对象类型
 * @param ownerRef    证据所属业务对象标识
 * @param mediaType   内容媒体类型，例如 {@code text/plain}
 * @param contentHash 原始内容的 SHA-256 摘要，用于完整性核验
 * @param dataClass   数据分级
 * @param createdAt   证据创建时间
 */
public record EvidenceReference(
        String evidenceRef,
        EvidenceType type,
        String ownerType,
        String ownerRef,
        String mediaType,
        String contentHash,
        EvidenceDataClass dataClass,
        Instant createdAt) {

    /**
     * 校验证据索引的完整性。
     */
    public EvidenceReference {
        evidenceRef = ContractChecks.maxLength(
                ContractChecks.notBlank(evidenceRef, "evidenceRef"), 96, "evidenceRef");
        type = ContractChecks.required(type, "type");
        ownerType = ContractChecks.maxLength(
                ContractChecks.notBlank(ownerType, "ownerType"), 64, "ownerType");
        ownerRef = ContractChecks.maxLength(
                ContractChecks.notBlank(ownerRef, "ownerRef"), 96, "ownerRef");
        mediaType = ContractChecks.maxLength(
                ContractChecks.notBlank(mediaType, "mediaType"), 64, "mediaType");
        contentHash = ContractChecks.maxLength(
                ContractChecks.notBlank(contentHash, "contentHash"), 64, "contentHash");
        dataClass = ContractChecks.required(dataClass, "dataClass");
        createdAt = ContractChecks.required(createdAt, "createdAt");
    }
}
