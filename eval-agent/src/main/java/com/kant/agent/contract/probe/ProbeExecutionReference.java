package com.kant.agent.contract.probe;

import com.kant.agent.contract.common.ContractChecks;

/**
 * 逻辑探测与一次物理执行的组合引用。
 *
 * @param probeId     逻辑探测标识；未来可关联多次受控物理执行
 * @param executionId 本次物理执行标识
 */
public record ProbeExecutionReference(String probeId, String executionId) {

    /**
     * 校验两个层次的业务标识。
     */
    public ProbeExecutionReference {
        probeId = ContractChecks.maxLength(
                ContractChecks.notBlank(probeId, "probeId"), 64, "probeId");
        executionId = ContractChecks.maxLength(
                ContractChecks.notBlank(executionId, "executionId"), 96, "executionId");
    }
}
