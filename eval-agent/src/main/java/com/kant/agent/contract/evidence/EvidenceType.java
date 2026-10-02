package com.kant.agent.contract.evidence;

/**
 * 评测证据类型。
 */
public enum EvidenceType {

    /** 实际提交给被测目标的输入。 */
    PROBE_INPUT,

    /** 被测目标返回的原始响应。 */
    TARGET_RESPONSE,

    /** 目标调用异常的受控错误信息。 */
    TARGET_ERROR,

    /** 目标实际工具调用轨迹。 */
    TOOL_TRACE,

    /** 目标实际检索轨迹。 */
    RETRIEVAL_TRACE,

    /** 判定器读取的证据集合快照。 */
    ASSESSMENT_INPUT,

    /** 判定器的原始结构化输出。 */
    ASSESSMENT_OUTPUT
}
