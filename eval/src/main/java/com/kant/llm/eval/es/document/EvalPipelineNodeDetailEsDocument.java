package com.kant.llm.eval.es.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评测流水线节点日志 ES 文档。
 *
 * <p>文档 ID 与 eval_pipeline_node_detail.id 保持一致。节点快照字段以 JSON 字符串存储，
 * 第一阶段只用于详情展示，不参与 ES 检索，避免 JSON 动态 mapping 过度膨胀。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalPipelineNodeDetailEsDocument {

    /** ES 文档 ID，同时也是 MySQL eval_pipeline_node_detail.id。 */
    private Long id;

    /** 评测任务 ID。 */
    private Long taskId;

    /** 任务执行批次 ID。 */
    private Long taskDetailId;

    /** 评测结果明细 ID。 */
    private Long resultDetailId;

    /** 数据集样本 ID。 */
    private Long sampleId;

    /** 节点编码：MODEL_CALL、L1、L2、L3。 */
    private String nodeCode;

    /** 节点状态：RUNNING、PASSED、BLOCKED、FAILED、SKIPPED、STOPPED。 */
    private String status;

    /** 节点开始时间。 */
    private LocalDateTime startTime;

    /** 节点结束时间。 */
    private LocalDateTime endTime;

    /** 节点耗时，单位毫秒。 */
    private Integer latency;

    /** 节点错误信息或短路原因。 */
    private String errorMsg;

    /** 节点输入快照 JSON 字符串，只存储不索引。 */
    private String inputSnapshot;

    /** 节点输出快照 JSON 字符串，只存储不索引。 */
    private String outputSnapshot;

    /** 节点结构化结果 JSON 字符串，只存储不索引。 */
    private String nodeResult;

    /** MySQL 创建时间。 */
    private LocalDateTime createTime;

    /** MySQL 更新时间。 */
    private LocalDateTime updateTime;
}
