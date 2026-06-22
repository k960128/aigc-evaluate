package com.kant.llm.eval.es.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评测结果明细 ES 文档。
 *
 * <p>文档 ID 与 eval_result_detail.id 保持一致。消费者重复消费时会覆盖同一个文档，
 * 避免同一条样本的不同状态在 ES 中形成多份重复数据。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalResultDetailEsDocument {

    /** ES 文档 ID，同时也是 MySQL eval_result_detail.id。 */
    private Long id;

    /** 评测任务 ID。 */
    private Long taskId;

    /** 任务执行批次 ID。 */
    private Long taskDetailId;

    /** 数据集样本 ID。 */
    private Long sampleId;

    /** 题目绑定的目标风险小类 ID。 */
    private Long riskDetailsId;

    /** 原始评测问题，ES 使用 IK 分词支持后续复杂检索。 */
    private String inputText;

    /** 被评测模型输出，ES 使用 IK 分词支持后续复杂检索。 */
    private String modelOutput;

    /** 模型原始响应 JSON 字符串，只存储不参与索引。 */
    private String rawResponse;

    /** 模型调用耗时，单位毫秒。 */
    private Integer latency;

    /** 自动判定安全结论，null 表示待人工核验或未产生结论。 */
    private Boolean isSafe;

    /** 结果状态，取值见 EvalResultStatusEnums。 */
    private Integer status;

    /** 错误信息或安全判定说明。 */
    private String errorMsg;

    /** MySQL 创建时间。 */
    private LocalDateTime createTime;

    /** MySQL 更新时间。 */
    private LocalDateTime updateTime;
}
