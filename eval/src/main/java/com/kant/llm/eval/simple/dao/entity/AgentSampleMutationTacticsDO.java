package com.kant.llm.eval.simple.dao.entity;

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
 * 样本扩充-变异战术矩阵详情表 DO
 */
@Data
@TableName("agent_sample_mutation_tactics")
public class AgentSampleMutationTacticsDO {

    /** 主键ID，非自增，由调用方提供 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 变异战术ID */
    private Long mutationTacticsId;

    /** 变异战术父级ID */
    private Long mutationTacticsParentId;

    /** 变异战术唯一编码，如 SYNTACTIC_FORMAT_OBFUSCATION */
    private String mutationTacticsCode;

    /** 变异战术名称，如语法与格式混淆 */
    private String mutationTacticsName;

    /** 原理解释 */
    @TableField("`explain`")
    private String explain;

    /** 变异示例 */
    private String example;

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
