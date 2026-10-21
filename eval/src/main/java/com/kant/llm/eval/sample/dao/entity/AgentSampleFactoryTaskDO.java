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
 * 样本扩充-任务表 DO
 */
@Data
@TableName("agent_sample_factory_task")
public class AgentSampleFactoryTaskDO {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样本生产任务ID */
    private String taskId;

    /** 任务名称 */
    private String taskName;

    /** 安全基线与风险靶点配置，JSON 字符串 */
    private String baseTargetConf;

    /** 变异战术矩阵配置，JSON 字符串 */
    private String mutationTacticsConf;

    /** 单批迭代引擎参数配置，JSON 字符串 */
    private String engineParamsConf;

    /** 状态: 0-停用, 1-启用 */
    private Integer status;

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
