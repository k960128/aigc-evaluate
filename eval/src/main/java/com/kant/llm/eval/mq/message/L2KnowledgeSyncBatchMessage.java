package com.kant.llm.eval.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * L2 知识库一键同步批次消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L2KnowledgeSyncBatchMessage {

    /** 同步批次 ID。 */
    private String batchId;

    /** 触发来源，例如 ONE_CLICK。 */
    private String triggerType;

    /** 触发时间。 */
    private LocalDateTime triggerTime;
}
