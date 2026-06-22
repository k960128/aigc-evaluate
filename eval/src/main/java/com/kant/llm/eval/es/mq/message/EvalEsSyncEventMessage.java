package com.kant.llm.eval.es.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评测 ES 投影同步 MQ 消息。
 *
 * <p>消息体只携带 eventId，消费者必须回查 eval_es_sync_event 和业务表最新数据。
 * 这样即使 MQ 乱序或重复投递，也不会把旧状态覆盖到 ES。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalEsSyncEventMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** outbox 事件唯一 ID。 */
    private String eventId;
}
