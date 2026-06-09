package com.kant.llm.eval.service.l2.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * L2 判定请求。
 *
 * <p>请求同时携带原始样本输入、模型输出和 L1 warning 标签，
 * 便于 L2 构造更完整的召回查询文本。</p>
 */
@Data
@Builder
public class L2EvaluationRequest {

    /** 评测任务 ID。 */
    private Long taskId;

    /** 任务执行批次 ID。 */
    private Long taskDetailId;

    /** 评测结果明细 ID。 */
    private Long resultDetailId;

    /** 数据集样本 ID。 */
    private Long sampleId;

    /** 用户原始输入或数据集样本输入。 */
    private String inputText;

    /** 被测模型输出内容。 */
    private String modelOutput;

    /** L1 warning 命中的风险小类 ID 列表。 */
    private List<Long> l1WarningTags;
}
