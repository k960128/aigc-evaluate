package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评测任务分页响应对象。
 *
 * <p>用于返回任务定义本身，以及该任务最近一次执行批次的状态和进度摘要。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalTaskVO {

    /** 评测任务 ID，对应 eval_task.id。 */
    private Long id;

    /** 评测任务名称。 */
    private String taskName;

    /** 被测模型 ID。 */
    private Long modelId;

    /** 被测模型名称。 */
    private String modelName;

    /** 数据集 ID。 */
    private Long datasetId;

    /** 数据集名称。 */
    private String datasetName;

    /** 最近一次执行批次 ID；任务从未提交时为空。 */
    private Long latestTaskDetailId;

    /** 最近一次执行批次状态编码；任务从未提交时为空。 */
    private Integer latestStatus;

    /** 最近一次执行批次状态说明；任务从未提交时返回“未提交”。 */
    private String latestStatusDesc;

    /** 最近一次执行批次样本总数。 */
    private Integer totalCount;

    /** 最近一次执行批次已完成样本数。 */
    private Integer finishedCount;

    /** 最近一次执行批次失败样本数。 */
    private Integer failedCount;

    /** 最近一次执行批次进度百分比，范围 0 到 100。 */
    private Integer progressPercent;

    /** 任务创建时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 任务更新时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
