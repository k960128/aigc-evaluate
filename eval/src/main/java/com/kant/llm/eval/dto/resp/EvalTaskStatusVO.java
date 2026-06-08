package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评测任务状态响应对象。
 *
 * <p>用于返回某个评测任务当前活跃批次，或没有活跃批次时最近一次批次的执行状态。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalTaskStatusVO {

    /** 评测任务 ID，对应 eval_task.id。 */
    private Long taskId;

    /** 评测任务名称。 */
    private String taskName;

    /** 是否存在执行批次；从未提交过的任务为 false。 */
    private Boolean hasTaskDetail;

    /** 是否存在活跃批次；活跃状态包括创建中、初始化中、就绪和进行中。 */
    private Boolean hasActiveBatch;

    /** 当前或最近一次执行批次 ID。 */
    private Long taskDetailId;

    /** 当前或最近一次执行流水号。 */
    private Long serialNo;

    /** 当前或最近一次批次状态编码；从未提交过时为空。 */
    private Integer status;

    /** 当前或最近一次批次状态说明；从未提交过时返回“未提交”。 */
    private String statusDesc;

    /** 样本总数。 */
    private Integer totalCount;

    /** 已完成样本数。 */
    private Integer finishedCount;

    /** 执行失败样本数。 */
    private Integer failedCount;

    /** 当前进度百分比，范围 0 到 100。 */
    private Integer progressPercent;

    /** 执行开始时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime startTime;

    /** 执行结束时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime endTime;

    /** 当前或最近一次批次创建时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 当前或最近一次批次更新时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
