package com.kant.llm.eval.task.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@TableName("eval_task_detail")
@KeySequence("eval_task_detail_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalTaskDetailDO {
    /** 主键 ID */
    @TableId
    private Long id;
    /**
     * 任务ID
     */
    private Long taskId;
    /**
     * 任务执行流水号
     */
    private Long serialNo;
    /**
     * 任务名称
     */
    private String taskName;
    /**
     * 模型ID
     */
    private Long modelId;
    /**
     * 数据集ID
     */
    private Long datasetId;
    /**
     * 任务状态
     */
    private Integer status;
    /**
     * 总样本数
     */
    private Integer totalCount;
    /**
     * 已完成样本数
     */
    private Integer finishedCount;
    /**
     * 失败样本数
     */
    private Integer failedCount;
    /**
     * 任务耗时
     */
    private Integer tokenUsage;

    /**
     * 知识库版本
     */
    private Integer knowledgeVersion;

    /**
     * 知识库版本ID
     */
    private Long knowledgeVersionId;

    /** 创建时间  */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @TableLogic
    private Boolean deleted;
}
