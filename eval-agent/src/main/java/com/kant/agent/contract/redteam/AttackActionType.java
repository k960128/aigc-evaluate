package com.kant.agent.contract.redteam;

/**
 * D02 自适应单轮攻击允许产生的动作类型。
 */
public enum AttackActionType {

    /** 在新的、干净的目标上下文中发送一条输入。 */
    SEND_INPUT,

    /** 结束当前攻击尝试。 */
    END_ATTEMPT
}
