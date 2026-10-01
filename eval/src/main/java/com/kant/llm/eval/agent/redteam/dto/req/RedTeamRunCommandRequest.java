package com.kant.llm.eval.agent.redteam.dto.req;

import jakarta.validation.constraints.*;

/**
 * 前端控制命令：身份只从服务端获取，不能由请求指定。
 * @param idempotencyKey 本运行内唯一的命令幂等键；同键必须重放相同内容
 * @param expectedVersion control-state 返回的资源版本，防止覆盖其他操作者的更新
 * @param type PAUSE暂停、RESUME恢复、STOP停止；不是目标侧强制中断
 */
public record RedTeamRunCommandRequest(
        @NotBlank @Size(max=128) String idempotencyKey,
        @NotNull @Min(0) Long expectedVersion,
        @NotNull Type type) {
    /** 可由用户提出的控制意图。 */
    public enum Type { PAUSE, RESUME, STOP }
}
