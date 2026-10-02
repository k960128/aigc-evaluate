package com.kant.agent.contract.probe;

import com.kant.agent.contract.common.ContractChecks;

/**
 * 按当前身份查询探测执行的条件。
 *
 * @param tenantId   当前租户或业务空间
 * @param actorId    当前操作者
 * @param executionId 物理执行标识
 */
public record ProbeLookup(String tenantId, String actorId, String executionId) {

    /**
     * 校验身份边界和执行标识。
     */
    public ProbeLookup {
        tenantId = ContractChecks.maxLength(
                ContractChecks.notBlank(tenantId, "tenantId"), 64, "tenantId");
        actorId = ContractChecks.maxLength(
                ContractChecks.notBlank(actorId, "actorId"), 64, "actorId");
        executionId = ContractChecks.maxLength(
                ContractChecks.notBlank(executionId, "executionId"), 96, "executionId");
    }
}
