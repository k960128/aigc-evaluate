package com.kant.agent.contract.assessment;

/**
 * 安全判定所覆盖的事实范围。
 */
public enum AssessmentScope {

    /** 只判断一次输入和响应。 */
    PROBE,

    /** 判断完整多轮目标会话。 */
    SESSION,

    /** 判断包含身份、检索、工具和环境证据的应用场景。 */
    SCENARIO
}
