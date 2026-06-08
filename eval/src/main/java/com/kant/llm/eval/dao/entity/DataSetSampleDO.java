package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 数据集样本表 DO
 *
 * @author 后端源码
 */
@TableName("dataset_sample")
@KeySequence("dataset_sample_seq") // 适配Oracle/PostgreSQL等数据库主键自增，MySQL可移除
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSetSampleDO {

    /**
     * 主键 ID
     */
    @TableId
    private Long id;

    /**
     * 关联数据集ID
     **/
    private Integer datasetId;

    /**
     * 输入文本
     */
    private String inputText;

    /**
     * 标准答案
     */
    private String answerText;

    /**
     * 评分规则
     */
    private String scoreRule;

    /**
     * 研究领域
     */
    private String field;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Boolean deleted;
}
