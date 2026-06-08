package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评测流水线节点日志响应对象。
 *
 * <p>用于返回单条样本在模型调用、L1、L2、L3 等节点上的执行历史和结构化结果。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalPipelineNodeDetailVO {

    /** 节点日志 ID，对应 eval_pipeline_node_detail.id。 */
    private Long id;

    /** 评测任务 ID，对应 eval_task.id。 */
    private Long taskId;

    /** 任务执行批次 ID，对应 eval_task_detail.id。 */
    private Long taskDetailId;

    /** 评测结果明细 ID，对应 eval_result_detail.id。 */
    private Long resultDetailId;

    /** 数据集样本 ID，对应 dataset_sample.id。 */
    private Long sampleId;

    /** 节点编码，例如 MODEL_CALL、L1、L2、L3。 */
    private String nodeCode;

    /** 节点编码说明。 */
    private String nodeCodeDesc;

    /** 节点状态，例如 RUNNING、PASSED、BLOCKED、FAILED、SKIPPED、STOPPED。 */
    private String status;

    /** 节点状态说明。 */
    private String statusDesc;

    /** 节点开始时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime startTime;

    /** 节点结束时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime endTime;

    /** 节点耗时，单位毫秒。 */
    private Integer latency;

    /** 节点错误信息或短路原因。 */
    private String errorMsg;

    /** 节点输入快照，优先返回解析后的 JSON 对象，解析失败时返回原始字符串。 */
    private Object inputSnapshot;

    /** 节点输出快照，优先返回解析后的 JSON 对象，解析失败时返回原始字符串。 */
    private Object outputSnapshot;

    /** 节点结构化结果，优先返回解析后的 JSON 对象，解析失败时返回原始字符串。 */
    private Object nodeResult;

    /** 节点日志创建时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 节点日志更新时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
