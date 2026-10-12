package com.kant.llm.eval.agent.production.error;
import com.kant.framework.errorcode.IErrorCode;
/** 样本工厂独立错误空间，不将质检失败解释为被测目标安全结论。 */
public enum SampleFactoryErrorCode implements IErrorCode {
    /**
     * 接口错误码用于同步控制面请求；批次内部errorCode保留异步执行原因，两者分开避免前端把“请求成功”误读为“批次成功”。
     */
    /** 资源不存在或当前主体不可见，统一消息避免泄露他人资源存在性。 */
    NOT_FOUND("A053001","样本生产资源不存在或无权访问"),
    /** 请求中的expectedVersion落后于计划、批次或候选当前版本。 */
    VERSION_CONFLICT("A053002","资源版本冲突"),
    /** 同一幂等键已用于不同请求，不能当作普通重试覆盖首次事实。 */
    IDEMPOTENCY_CONFLICT("A053003","样本工厂幂等键冲突"),
    /** 参数、引用或当前状态不允许执行该动作，例如直接审核重复候选。 */
    INVALID_REQUEST("A053004","生产参数或状态不允许"),
    /** 生成或评审角色不可用，或组合指纹与计划不一致；保留原错误码，不是预算错误。 */
    GENERATOR_UNAVAILABLE("A053005","生成或评审角色未启用、未配置模型，或配置快照已变化"),
    /** 事实解析、关联核对或持久化失败，固定消息不暴露SQL及私有正文。 */
    PERSISTENCE_ERROR("B053001","样本生产事实读取或保存失败");
    /** 对外稳定业务错误码，与批次中的执行errorCode不是同一枚举。 */
    private final String code;
    /** 可安全展示的固定中文提示，不拼接种子、配置凭据或供应商异常。 */
    private final String message;
    /** 绑定稳定错误码与展示文案。 */
    SampleFactoryErrorCode(String code,String message) { this.code=code; this.message=message; }
    /** 返回接口业务错误码。 */
    public String code() { return code; }
    /** 返回固定中文提示。 */
    public String message() { return message; }
}
