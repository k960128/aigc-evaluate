package com.kant.llm.eval.agent.probe.error;

import com.kant.framework.errorcode.IErrorCode;

/**
 * 共享探测执行的稳定错误码。
 *
 * <p>A 类错误表示调用方需要修改请求，B 类错误表示平台未能按预期完成处理。
 * 已经受理的目标调用失败不会通过这些错误码覆盖执行事实，而是写入探测记录。</p>
 */
public enum AgentProbeErrorCode implements IErrorCode {

    /** 请求字段或字段组合不符合探测契约。 */
    INVALID_PROBE_REQUEST("A050001", "探测请求参数不合法"),

    /** 同一租户中的幂等键已被另一组参数使用。 */
    IDEMPOTENCY_CONFLICT("A050002", "幂等键对应的请求参数不一致"),

    /** 目标或当前适配器不支持请求所需能力。 */
    TARGET_CAPABILITY_UNSUPPORTED("A050003", "目标能力不支持当前请求"),

    /** 指定探测执行不存在或当前身份不可访问。 */
    PROBE_NOT_FOUND("A050004", "探测执行不存在"),

    /** 指定证据不存在、归属不匹配或当前身份不可读取。 */
    EVIDENCE_NOT_FOUND("A050005", "证据不存在或无权访问"),

    /** 目标配置不存在、已停用或无法解析。 */
    TARGET_UNAVAILABLE("A050006", "被测目标当前不可用"),

    /** 目标配置在受理与执行之间发生了变化。 */
    TARGET_SNAPSHOT_CHANGED("A050007", "目标配置已变化，请重新创建探测"),

    /** 探测执行事实保存失败。 */
    PROBE_PERSISTENCE_ERROR("B050001", "探测执行记录保存失败"),

    /** 探测状态发生并发变化，当前操作无法继续。 */
    PROBE_STATE_CONFLICT("B050002", "探测执行状态冲突");

    /** 对外错误码。 */
    private final String code;

    /** 默认中文错误信息。 */
    private final String message;

    AgentProbeErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
