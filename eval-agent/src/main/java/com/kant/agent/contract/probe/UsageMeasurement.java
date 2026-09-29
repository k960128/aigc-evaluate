package com.kant.agent.contract.probe;

/**
 * 消耗数据的计量来源。
 */
public enum UsageMeasurement {

    /** 由目标或模型服务返回的真实计量。 */
    ACTUAL,

    /** 根据公开规则或本地分词得到的估算值。 */
    ESTIMATED,

    /** 当前执行适配器无法获得该计量。 */
    UNAVAILABLE
}
