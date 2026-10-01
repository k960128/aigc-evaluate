package com.kant.agent.contract.redteam;

import com.kant.agent.contract.probe.ProbeCommand;

/**
 * 可恢复的目标操作日志。正文仅保存在受限数据库字段，禁止返回公开接口或日志。
 * @param sequence 动作序号
 * @param actionId 审计动作标识
 * @param action 已通过代码校验的攻击动作
 * @param command 固定幂等键、截止时间及完整输入的执行命令
 * @param reservedTokens 此次操作预留的完整上下文输入Token
 * @param status PREPARED未发出、DISPATCHING可能已受理、SETTLED已结算
 */
public record PendingProbe(int sequence, String actionId, AttackAction action,
                           ProbeCommand command, long reservedTokens, String status) {
    public PendingProbe {
        if (sequence < 1 || actionId == null || action == null || command == null
                || reservedTokens < 0 || !java.util.Set.of("PREPARED", "DISPATCHING", "SETTLED").contains(status)) {
            throw new IllegalArgumentException("待执行操作日志不完整");
        }
    }
    /** 不更改执行输入及幂等键，只推进日志状态。 */
    public PendingProbe withStatus(String value) {
        return new PendingProbe(sequence, actionId, action, command, reservedTokens, value);
    }
}
