package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mock_model_output_content")
public class MockModelOutputContentDO {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的模型ID */
    private Long bindModelId;

    /** 关联的模型输出文本 */
    private String outputText;

    /**
     * 关联评测样本ID
     */
    private Long bindSampleId;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 是否删除 0未删除 1删除 */
    @TableLogic
    private Boolean deleted;
}
