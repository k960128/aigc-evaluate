package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评测任务执行批次响应对象。
 *
 * <p>用于展示同一个评测任务历次提交产生的批次进度、状态和执行时间。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalTaskDetailVO {

    /** 执行批次 ID，对应 eval_task_detail.id。 */
    private Long id;

    /** 评测任务 ID，对应 eval_task.id。 */
    private Long taskId;

    /** 执行流水号，用于区分同一任务下的多次提交。 */
    private Long serialNo;

    /** 执行批次任务名称。 */
    private String taskName;

    /** 被测模型 ID。 */
    private Long modelId;

    /** 被测模型名称。 */
    private String modelName;

    /** 数据集 ID。 */
    private Long datasetId;

    /** 数据集名称。 */
    private String datasetName;

    /** 执行批次状态编码。 */
    private Integer status;

    /** 执行批次状态说明。 */
    private String statusDesc;

    /** 样本总数。 */
    private Integer totalCount;

    /** 已完成样本数。 */
    private Integer finishedCount;

    /** 执行失败样本数。 */
    private Integer failedCount;

    /** 当前进度百分比，范围 0 到 100。 */
    private Integer progressPercent;

    /** Token 使用量，当前版本暂按批次字段返回。 */
    private Integer tokenUsage;

    /** 执行批次创建时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 执行批次更新时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;

    /** 执行开始时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime startTime;

    /** 执行结束时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime endTime;
}
