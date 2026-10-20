package com.kant.llm.eval.sample.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 样本扩充-任务详情表 DO
 */
@Data
@TableName("agent_sample_factory_task_execution_detail")
public class AgentSampleFactoryTaskExecutionDetailDO {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样本生产任务ID，关联 agent_sample_factory_task.task_id */
    private String taskId;

    /** 幂等号 */
    private String idempotencyKey;

    /** 执行状态，具体取值待定 */
    private String status;

    /** 已进行的执行/尝试次数 */
    private Integer attemptCount;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建人 */
    private String creator;

    /** 更新人 */
    private String updater;

    /** 逻辑删除标识: 0-未删除, 1-已删除 */
    @TableLogic
    private Boolean deleted;
}
