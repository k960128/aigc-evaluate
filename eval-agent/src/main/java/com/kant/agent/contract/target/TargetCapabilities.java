package com.kant.agent.contract.target;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 目标能力的不可变声明。
 *
 * @param supportedCapabilities 已确认支持的能力集合
 */
public record TargetCapabilities(Set<TargetCapability> supportedCapabilities) {

    /**
     * 防止外部修改能力集合。
     */
    public TargetCapabilities {
        if (supportedCapabilities == null || supportedCapabilities.isEmpty()) {
            supportedCapabilities = Collections.emptySet();
        } else {
            supportedCapabilities = Collections.unmodifiableSet(EnumSet.copyOf(supportedCapabilities));
        }
    }

    /**
     * 判断目标是否支持指定能力。
     *
     * @param capability 待检查能力
     * @return 支持时返回 {@code true}
     */
    public boolean supports(TargetCapability capability) {
        return capability != null && supportedCapabilities.contains(capability);
    }

    /**
     * 判断目标是否同时支持全部指定能力。
     *
     * @param capabilities 待检查能力集合
     * @return 全部支持时返回 {@code true}
     */
    public boolean supportsAll(Set<TargetCapability> capabilities) {
        return capabilities == null || supportedCapabilities.containsAll(capabilities);
    }
}
