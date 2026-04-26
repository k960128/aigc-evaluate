package com.kant.llm.eval.dao.entity;

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
    private Long taskId;
    /**
     * 任务执行流水号
     */
    private Long serialNo;
    private String taskName;
    private Long modelId;
    private Long datasetId;
    private Integer status;
    private Integer totalCount;
    private Integer finishedCount;
    private Integer failedCount;
    private Integer tokenUsage;
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
