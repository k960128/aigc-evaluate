package com.kant.llm.eval.pipeline.record;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单样本模型调用消息。
 *
 * <p>该消息只负责把样本送入被评测模型调用阶段。模型返回后，消费者会把输出写入
 * eval_result_detail，再投递 SafetyEvaluateMessage 进入 L1/L2/L3。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCallMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 评测任务执行批次 ID。 */
    private Long taskDetailId;

    /** 单条评测结果明细 ID。 */
    private Long resultDetailId;

    private Integer executionVersion;

    /** 事务消息回查所用的人工重试请求 ID。 */
    private String retryRequestId;

    /** 评测任务 ID。 */
    private Long taskId;

    /** 数据集样本 ID。 */
    private Long sampleId;

    /** 被评测模型 ID。 */
    private Long modelId;
}
