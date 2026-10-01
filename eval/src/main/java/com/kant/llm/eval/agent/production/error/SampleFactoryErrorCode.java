package com.kant.llm.eval.agent.production.error;
import com.kant.framework.errorcode.IErrorCode;
/** 样本工厂独立错误空间，不将质检失败解释为被测目标安全结论。 */
public enum SampleFactoryErrorCode implements IErrorCode {
    NOT_FOUND("A053001","样本生产资源不存在或无权访问"),
    VERSION_CONFLICT("A053002","资源版本冲突"),
    IDEMPOTENCY_CONFLICT("A053003","样本工厂幂等键冲突"),
    INVALID_REQUEST("A053004","生产参数或状态不允许"),
    GENERATOR_UNAVAILABLE("A053005","生成角色未启用或配置快照已变化"),
    PERSISTENCE_ERROR("B053001","样本生产事实读取或保存失败");
    private final String code; private final String message;
    SampleFactoryErrorCode(String code,String message) { this.code=code; this.message=message; }
    public String code() { return code; } public String message() { return message; }
}
