package com.kant.llm.eval.agent.redteam.error;

import com.kant.framework.errorcode.IErrorCode;

/**
 * D02 自动红队任务的稳定错误码。
 */
public enum AgentRedTeamErrorCode implements IErrorCode {

    /** 任务配置不符合 D02 契约。 */
    INVALID_TASK_REQUEST("A051001", "自动红队任务参数不合法"),

    /** 当前身份无法读取任务。 */
    TASK_NOT_FOUND("A051002", "自动红队任务不存在"),

    /** 同一租户幂等键对应了另一份任务配置。 */
    IDEMPOTENCY_CONFLICT("A051003", "自动红队幂等键对应的配置不一致"),

    /** 目标不具备 D02 所需能力。 */
    TARGET_CAPABILITY_UNSUPPORTED("A051004", "目标不支持自动红队所需能力"),

    /** 当前任务已有运行在执行。 */
    TASK_ALREADY_RUNNING("A051005", "自动红队任务已有运行正在执行"),

    /** 当前身份无法读取运行。 */
    RUN_NOT_FOUND("A051006", "自动红队运行不存在"),

    /** 运行状态不允许当前命令。 */
    RUN_STATE_CONFLICT("A051007", "自动红队运行状态冲突"),

    /** 当前目标适配器没有统一定价和费用计量，不能接受费用硬上限。 */
    BUDGET_METER_UNSUPPORTED("A051008", "当前目标适配器不支持费用预算强制"),

    /** 目标能力端口返回的快照没有对应当前请求的目标。 */
    TARGET_SNAPSHOT_INVALID("B051002", "目标快照与请求目标不一致"),

    /** 运行事实保存失败。 */
    RUN_PERSISTENCE_ERROR("B051001", "自动红队运行记录保存失败");

    private final String code;
    private final String message;

    AgentRedTeamErrorCode(String code, String message) {
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
