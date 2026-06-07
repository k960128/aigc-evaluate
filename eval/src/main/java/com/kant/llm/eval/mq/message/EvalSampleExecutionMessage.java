package com.kant.llm.eval.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单样本执行消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalSampleExecutionMessage {

    /** 本次任务运行批次 ID，保证同一个任务多次提交时结果不混批。 */
    private Long taskDetailId;

    /** 拆分后生成的单样本结果明细 ID，对应 eval_result_detail.id。 */
    private Long resultDetailId;

    /** 原始任务 ID，对应 eval_task.id。 */
    private Long taskId;

    /** 数据集样本 ID，对应 dataset_sample.id。 */
    private Long sampleId;

    /** 被测模型 ID，执行消费者根据它选择模型调用策略。 */
    private Long modelId;
}
