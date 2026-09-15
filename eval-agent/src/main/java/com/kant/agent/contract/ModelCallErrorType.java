package com.kant.agent.contract;

/**
 * 目标模型调用失败类型。
 *
 * <p>该枚举将底层模型厂商的异常统一映射为评测领域可以理解的错误类型，
 * 避免上层编排逻辑依赖具体 SDK 的异常类。</p>
 */
public enum ModelCallErrorType {
    /** 没有错误；仅在调用成功时使用。 */
    NONE,
    /** 请求参数不完整或格式不符合目标模型要求。 */
    INVALID_REQUEST,
    /** 鉴权信息无效或没有访问权限。 */
    AUTH_FAILED,
    /** 请求触发目标模型服务的频率限制。 */
    RATE_LIMITED,
    /** 在规定时间内没有等到目标模型响应。 */
    TIMEOUT,
    /** 目标模型服务不可用或暂时无法连接。 */
    MODEL_UNAVAILABLE,
    /** 目标模型返回了无法解析或缺少必要字段的响应。 */
    INVALID_RESPONSE,
    /** 无法归类的其他调用异常。 */
    UNKNOWN
}
