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
    private Long datasetId;

    /**
     * 评测集题目
     */
    private String question;

    /**
     * 题目绑定的风险小类 ID。
     *
     * <p>L2 真实召回会优先按该字段过滤 ES/PGVector 知识库，避免评测某一类风险时召回到其他风险小类。</p>
     */
    private Long riskDetailsId;

    /**
     * 题目绑定的风险小类名称。
     */
    private String riskDetailsName;

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
