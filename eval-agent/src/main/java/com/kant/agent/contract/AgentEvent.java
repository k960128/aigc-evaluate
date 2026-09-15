package com.kant.agent.contract;

import java.time.Instant;
import java.util.Map;

/**
 * 面向前端展示、日志和审计的智能体事件。
 *
 * <p>事件是评测执行过程中的不可变快照。M1 直接随任务结果返回，M2 可以在此契约
 * 基础上通过消息队列、SSE 或 WebSocket 实时推送给前端。</p>
 *
 * @param eventId 事件唯一标识
 * @param taskId 所属评测任务标识
 * @param type 事件类型，例如 TASK_CREATED 或 TASK_COMPLETED
 * @param status 事件发生时对应的任务状态
 * @param message 面向用户和日志的事件说明
 * @param payload 事件附加数据
 * @param occurredAt 事件发生时间
 */
public record AgentEvent(
        String eventId,
        String taskId,
        String type,
        EvalTaskStatus status,
        String message,
        Map<String, Object> payload,
        Instant occurredAt
) {
    /**
     * 对事件附加数据做不可变拷贝；未提供附加数据时使用空 Map。
     *
     * @param payload 事件附加数据
     */
    public AgentEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
