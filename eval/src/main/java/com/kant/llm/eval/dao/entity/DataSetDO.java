package com.kant.llm.eval.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 数据集信息 DO
 *
 * @author 后端源码
 */
@TableName("dataset_info")
@KeySequence("dataset_info_seq") // 适配Oracle/PostgreSQL等数据库主键自增，MySQL可移除
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSetDO {

    /** 主键 ID */
    @TableId
    private Long id;

    /** 数据集名称 */
    private String datasetName;

    /** 评测类型 */
    private Integer datasetType;

    /** 样本数量 */
    private Integer sampleCount;

    /** 数据集描述 */
    private String description;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Boolean deleted;

}
