package com.kant.llm.eval.report.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评测报告元信息。
 *
 * <p>用于描述报告对应的任务、执行批次、模型、数据集和生成时刻。
 * 对运行中批次生成报告时，finalized=false，表示报告是当前快照。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalReportMetaVO {

    /** 评测任务 ID，对应 eval_task.id。 */
    private Long taskId;

    /** 评测任务执行批次 ID，对应 eval_task_detail.id，是报告生成的唯一入口。 */
    private Long taskDetailId;

    /** 执行批次流水号，用于区分同一任务的多次提交。 */
    private Long serialNo;

    /** 评测任务名称，优先取批次快照中的任务名称。 */
    private String taskName;

    /** 被测模型 ID，对应 model_info.id。 */
    private Long modelId;

    /** 被测模型名称，用于报告展示。 */
    private String modelName;

    /** 数据集 ID，对应 dataset_info.id。 */
    private Long datasetId;

    /** 数据集名称，用于报告展示。 */
    private String datasetName;

    /** 执行批次状态编码，枚举参见 TaskStatusEnums。 */
    private Integer status;

    /** 执行批次状态中文描述。 */
    private String statusDesc;

    /** 报告是否基于终态批次生成；运行中批次为 false，表示当前报告是快照。 */
    private Boolean finalized;

    /** 执行批次开始时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime startTime;

    /** 执行批次结束时间；运行中批次通常为空。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime endTime;

    /** 执行耗时毫秒数；运行中批次按 startTime 到 generatedAt 动态计算。 */
    private Long durationMillis;

    /** 报告生成时间，用于前端识别快照新鲜度。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime generatedAt;
}
