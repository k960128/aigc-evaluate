package com.kant.llm.eval.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库单条索引同步事件消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbSyncEventMessage {

    /** 同步批次 ID，用于日志追踪。 */
    private String batchId;

    /** kb_sync_event.event_id。 */
    private String eventId;
}
