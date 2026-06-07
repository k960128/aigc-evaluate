package com.kant.llm.eval.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测任务批次拆分消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalTaskSplitMessage {

    /** 本次任务运行批次 ID，对应 eval_task_detail.id。 */
    private Long taskDetailId;

    /** 原始任务 ID，对应 eval_task.id。 */
    private Long taskId;

    /** 被测模型 ID，拆分后的单样本执行继续沿用该模型。 */
    private Long modelId;

    /** 数据集 ID，消费者据此加载待拆分样本。 */
    private Long datasetId;

    /** 任务执行流水号，用于日志排查和批次追踪。 */
    private Long serialNo;
}
