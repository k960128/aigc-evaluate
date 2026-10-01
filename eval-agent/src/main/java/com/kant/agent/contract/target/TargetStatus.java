package com.kant.agent.contract.target;

/**
 * 被测目标在快照时刻的可用状态。
 */
public enum TargetStatus {

    /** 目标配置有效并允许执行。 */
    AVAILABLE,

    /** 目标配置已停用。 */
    DISABLED,

    /** 目标配置存在，但当前不可用。 */
    UNAVAILABLE
}
