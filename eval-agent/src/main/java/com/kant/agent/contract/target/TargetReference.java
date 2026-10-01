package com.kant.agent.contract.target;

import com.kant.agent.contract.common.ContractChecks;

/**
 * 被测目标的稳定引用。
 *
 * @param targetType 目标类型
 * @param targetId   目标在现有资源系统中的标识
 */
public record TargetReference(TargetType targetType, String targetId) {

    /**
     * 校验目标类型与资源标识。
     */
    public TargetReference {
        targetType = ContractChecks.required(targetType, "targetType");
        targetId = ContractChecks.maxLength(
                ContractChecks.notBlank(targetId, "targetId"), 64, "targetId");
    }
}
