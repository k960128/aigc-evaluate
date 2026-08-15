package com.kant.llm.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评测 ES 投影同步事件状态枚举。
 *
 * <p>同步事件不设置处理中状态，调度和 MQ 都允许重复投递；消费者每次回查 MySQL 最新数据后覆盖写入 ES，
 * 因此重复消费不会产生重复文档，只会把读模型推进到最新状态。</p>
 */
@Getter
@AllArgsConstructor
public enum EvalEsSyncStatusEnums {

    /** 待同步：业务写 MySQL 后刚记录的 outbox 事件。 */
    PENDING(0, "待同步"),

    /** 已同步：消费者已经把 MySQL 最新数据投影到 ES。 */
    SYNCED(1, "已同步"),

    /** 同步失败：消费者执行异常，等待调度器按 nextRetryTime 重新投递。 */
    FAILED(2, "同步失败");

    private final Integer code;

    private final String desc;
}
