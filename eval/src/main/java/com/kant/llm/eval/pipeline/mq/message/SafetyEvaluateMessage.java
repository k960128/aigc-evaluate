package com.kant.llm.eval.pipeline.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单样本安全评测消息。
 *
 * <p>该消息只在模型输出已经落库后发送。消费者读取 eval_result_detail 中的
 * model_output，并继续执行 L1/L2/L3 流水线。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyEvaluateMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 评测任务执行批次 ID。 */
    private Long taskDetailId;

    /** 单条评测结果明细 ID。 */
    private Long resultDetailId;

    /** 评测任务 ID。 */
    private Long taskId;

    /** 数据集样本 ID。 */
    private Long sampleId;

    /** 被评测模型 ID。 */
    private Long modelId;
}
