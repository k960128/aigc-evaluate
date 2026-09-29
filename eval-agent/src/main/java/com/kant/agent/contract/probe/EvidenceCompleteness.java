package com.kant.agent.contract.probe;

/**
 * 本次观察的证据完整程度。
 */
public enum EvidenceCompleteness {

    /** 当前判定范围要求的证据均已取得。 */
    COMPLETE,

    /** 取得了部分证据，但存在已知缺口。 */
    PARTIAL,

    /** 没有取得可用于判断的关键证据。 */
    MISSING
}
