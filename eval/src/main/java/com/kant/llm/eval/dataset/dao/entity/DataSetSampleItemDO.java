package com.kant.llm.eval.dataset.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 数据集样本关联关系表 DO
 *
 * @author 后端源码
 */
@TableName("dataset_sample_item")
@KeySequence("dataset_sample_item_seq") // 适配Oracle/PostgreSQL等数据库主键自增，MySQL可移除
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSetSampleItemDO {

    /**
     * 主键 ID
     */
    @TableId
    private Long id;


    /**
     * 数据集ID
     */
    private Long datasetId;

    /**
     * 样本ID
     */
    private Long datasetSampleId;

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
