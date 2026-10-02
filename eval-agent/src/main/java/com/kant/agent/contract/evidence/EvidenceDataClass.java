package com.kant.agent.contract.evidence;

/**
 * 证据数据分级，用于访问控制、脱敏和保留策略。
 */
public enum EvidenceDataClass {

    /** 可在项目内部普通展示的数据。 */
    INTERNAL,

    /** 只允许具备评测权限的人员读取的数据。 */
    CONFIDENTIAL,

    /** 原始提示、响应和行为轨迹等严格受限数据。 */
    RESTRICTED
}
