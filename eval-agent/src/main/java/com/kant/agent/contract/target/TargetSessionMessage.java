package com.kant.agent.contract.target;

import com.kant.agent.contract.common.ContractChecks;

/**
 * 目标会话中的一条已确认消息。
 *
 * <p>会话历史只允许由已经保存的目标执行事实构造，不能由攻击 Agent 自行
 * 编造。未知或尚未完成的目标调用不会进入可继续使用的会话历史。</p>
 *
 * @param sequence 消息在会话中的顺序，从1开始
 * @param role 消息角色
 * @param content 消息正文
 */
public record TargetSessionMessage(
        int sequence,
        TargetSessionMessageRole role,
        String content) {

    /** 校验并冻结一条会话消息。 */
    public TargetSessionMessage {
        ContractChecks.positive(sequence, "sequence");
        role = ContractChecks.required(role, "role");
        // 输入不能为空；助手空回复仍是真实执行事实，不能丢弃或编造替代正文。
        content = ContractChecks.maxLength(role == TargetSessionMessageRole.USER
                        ? ContractChecks.notBlankPreserving(content, "content")
                        : ContractChecks.required(content, "content"),
                role == TargetSessionMessageRole.USER ? 100_000 : 200_000, "content");
    }
}
