package com.kant.agent.contract.target;

import com.kant.agent.contract.common.ContractChecks;

import java.time.Instant;
import java.util.List;

/**
 * 某次执行所使用的目标配置快照。
 *
 * <p>快照只保存可审计元数据和凭据引用，禁止包含 API Key、密码等明文凭据。</p>
 *
 * @param snapshotRef   快照引用，同一配置版本应保持稳定
 * @param target        目标资源引用
 * @param displayName   目标展示名称
 * @param modelName     实际模型名称；非模型目标可为空
 * @param targetVersion 目标配置版本
 * @param status        快照时刻的目标状态
 * @param environment   测试环境；未知时明确使用 {@code UNSPECIFIED}
 * @param identityRef   测试身份或凭据配置引用，不含凭据本身
 * @param capabilities  已确认的目标能力
 * @param capturedAt    快照采集时间
 * @param limitations   当前适配器和证据能力的已知限制
 */
public record TargetSnapshot(
        String snapshotRef,
        TargetReference target,
        String displayName,
        String modelName,
        String targetVersion,
        TargetStatus status,
        String environment,
        String identityRef,
        TargetCapabilities capabilities,
        Instant capturedAt,
        List<String> limitations) {

    /**
     * 创建不可变且不包含空项的目标快照。
     */
    public TargetSnapshot {
        snapshotRef = ContractChecks.maxLength(
                ContractChecks.notBlank(snapshotRef, "snapshotRef"), 160, "snapshotRef");
        target = ContractChecks.required(target, "target");
        displayName = ContractChecks.maxLength(
                ContractChecks.notBlank(displayName, "displayName"), 256, "displayName");
        modelName = ContractChecks.maxLength(
                ContractChecks.nullableText(modelName), 256, "modelName");
        targetVersion = ContractChecks.maxLength(
                ContractChecks.notBlank(targetVersion, "targetVersion"), 128, "targetVersion");
        status = ContractChecks.required(status, "status");
        environment = ContractChecks.maxLength(
                ContractChecks.notBlank(environment, "environment"), 64, "environment");
        identityRef = ContractChecks.maxLength(
                ContractChecks.notBlank(identityRef, "identityRef"), 128, "identityRef");
        capabilities = ContractChecks.required(capabilities, "capabilities");
        capturedAt = ContractChecks.required(capturedAt, "capturedAt");
        limitations = limitations == null
                ? List.of()
                : limitations.stream().filter(item -> item != null && !item.isBlank()).map(String::trim).toList();
    }
}
