package com.kant.agent.contract.target;

import com.kant.agent.contract.common.ContractChecks;

import java.time.Instant;

/**
 * 目标侧会话的受控引用。
 *
 * @param sessionRef        平台侧会话引用
 * @param providerSessionRef 目标服务返回的会话引用；目标无服务端会话时可为空
 * @param targetSnapshotRef 会话绑定的目标快照
 * @param createdAt         会话创建时间
 */
public record TargetSessionReference(
        String sessionRef,
        String providerSessionRef,
        String targetSnapshotRef,
        Instant createdAt) {

    /**
     * 校验会话引用。
     */
    public TargetSessionReference {
        sessionRef = ContractChecks.maxLength(
                ContractChecks.notBlank(sessionRef, "sessionRef"), 96, "sessionRef");
        providerSessionRef = ContractChecks.maxLength(
                ContractChecks.nullableText(providerSessionRef), 160, "providerSessionRef");
        targetSnapshotRef = ContractChecks.maxLength(
                ContractChecks.notBlank(targetSnapshotRef, "targetSnapshotRef"), 160, "targetSnapshotRef");
        createdAt = ContractChecks.required(createdAt, "createdAt");
    }
}
