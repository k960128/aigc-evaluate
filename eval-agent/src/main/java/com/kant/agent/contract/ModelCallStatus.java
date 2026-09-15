package com.kant.agent.contract;

/**
 * 目标模型调用状态。
 */
public enum ModelCallStatus {
    /** 目标模型已正常返回响应。 */
    SUCCESS,
    /** 目标模型调用未成功，具体原因由 ModelCallErrorType 描述。 */
    FAILED
}
