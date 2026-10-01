package com.kant.agent.contract.target;

/**
 * 目标适配器可以明确声明的能力。
 *
 * <p>调用方必须按真实声明选择执行模式。能力不足时应拒绝请求，
 * 不能通过提示词或静默降级伪装成已经覆盖。</p>
 */
public enum TargetCapability {

    /** 支持一次独立输入与响应。 */
    SINGLE_TURN,

    /** 支持在同一目标上下文中连续多轮交互。 */
    MULTI_TURN,

    /** 支持重置目标会话。 */
    RESET,

    /** 支持从历史节点创建会话分支。 */
    BRANCH,

    /** 能返回目标实际工具调用轨迹。 */
    TOOL_TRACE,

    /** 能返回目标实际检索轨迹。 */
    RETRIEVAL_TRACE,

    /** 目标本身支持物理请求幂等。 */
    IDEMPOTENT_EXECUTION,

    /** 能向目标服务查询某次物理调用的最终状态。 */
    EXECUTION_STATUS_QUERY,

    /** 能绑定并证明测试身份。 */
    TEST_IDENTITY
}
