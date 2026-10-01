package com.kant.llm.eval.agent.finding.error;
import com.kant.framework.errorcode.IErrorCode;
/** D04稳定业务错误，不把证据缺失压成目标安全。 */
public enum AgentFindingErrorCode implements IErrorCode {
    /** 统一隐藏不存在与无权访问的差别。 */ FINDING_NOT_FOUND("A052001","发现不存在或无权访问"),
    /** 资源版本过时。 */ VERSION_CONFLICT("A052002","资源版本冲突，请读取对应对象最新版本"),
    /** 同键请求不同。 */ IDEMPOTENCY_CONFLICT("A052003","D04幂等键冲突"),
    /** 意见与人工动作不匹配。 */ INVALID_REVIEW("A052004","复核参数或状态不允许"),
    /** 证据不支持自动确认。 */ EVIDENCE_INSUFFICIENT("A052005","证据缺失或一致性检查未通过"),
    /** 归并地址、审核状态或整改流程不允许。 */ STATE_CONFLICT("A052006","当前发现或资产状态不允许该动作"),
    /** 历史目标条件、能力或快照不匹配。 */ TARGET_UNSUPPORTED("A052007","验证目标条件或能力不满足固定用例"),
    /** 数据库或快照解析失败。 */ PERSISTENCE_ERROR("B052001","发现记录读取或保存失败");
    private final String code; private final String message;
    AgentFindingErrorCode(String code,String message) { this.code=code; this.message=message; }
    public String code() { return code; }
    public String message() { return message; }
}
