package com.kant.llm.eval.agent.probe.dto.resp;

import com.kant.agent.contract.target.TargetSnapshot;

import java.time.Instant;
import java.util.List;

/**
 * 模型目标当前快照与能力响应。
 *
 * @param targetSnapshotRef    当前目标快照引用
 * @param targetType           目标类型
 * @param targetId             现有目标资源标识
 * @param displayName          目标展示名称
 * @param modelName            实际模型名称
 * @param targetVersion        目标配置版本
 * @param status               AVAILABLE、DISABLED 或 UNAVAILABLE
 * @param environment          测试环境；未配置时为 UNSPECIFIED
 * @param identityRef          凭据或测试身份引用，不包含明文凭据
 * @param supportedCapabilities 已确认支持的能力集合
 * @param capturedAt           当前快照采集时间
 * @param limitations          适配器和证据能力的已知限制
 */
public record TargetCapabilitiesResponse(
        String targetSnapshotRef,
        String targetType,
        String targetId,
        String displayName,
        String modelName,
        String targetVersion,
        String status,
        String environment,
        String identityRef,
        List<String> supportedCapabilities,
        Instant capturedAt,
        List<String> limitations) {

    /** 将领域目标快照转换为 API 响应。 */
    public static TargetCapabilitiesResponse from(TargetSnapshot snapshot) {
        List<String> capabilities = snapshot.capabilities().supportedCapabilities().stream()
                .map(Enum::name)
                .sorted()
                .toList();
        return new TargetCapabilitiesResponse(
                snapshot.snapshotRef(),
                snapshot.target().targetType().name(),
                snapshot.target().targetId(),
                snapshot.displayName(),
                snapshot.modelName(),
                snapshot.targetVersion(),
                snapshot.status().name(),
                snapshot.environment(),
                snapshot.identityRef(),
                capabilities,
                snapshot.capturedAt(),
                snapshot.limitations());
    }
}
