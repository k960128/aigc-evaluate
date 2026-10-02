package com.kant.agent.contract.evidence;

import com.kant.agent.contract.common.ContractChecks;

/**
 * 读取单条证据的授权查询条件。
 *
 * @param tenantId   当前租户或业务空间
 * @param actorId    当前操作者
 * @param ownerRef   证据所属执行标识
 * @param evidenceRef 证据引用
 */
public record EvidenceQuery(
        String tenantId,
        String actorId,
        String ownerRef,
        String evidenceRef) {

    /**
     * 校验查询边界，避免只凭证据 ID 越权读取。
     */
    public EvidenceQuery {
        tenantId = ContractChecks.maxLength(
                ContractChecks.notBlank(tenantId, "tenantId"), 64, "tenantId");
        actorId = ContractChecks.maxLength(
                ContractChecks.notBlank(actorId, "actorId"), 64, "actorId");
        ownerRef = ContractChecks.maxLength(
                ContractChecks.notBlank(ownerRef, "ownerRef"), 96, "ownerRef");
        evidenceRef = ContractChecks.maxLength(
                ContractChecks.notBlank(evidenceRef, "evidenceRef"), 96, "evidenceRef");
    }
}
