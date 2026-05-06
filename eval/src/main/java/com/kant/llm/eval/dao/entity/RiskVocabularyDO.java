package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@TableName("risk_vocabulary")
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskVocabularyDO {

    @TableId
    private Long id;

    /**
     * 所属风险明细ID (关联 risk_item.id)
     */
    private Long itemId;

    /**
     * 风险词汇
     */
    private String keyword;

    /**
     * 匹配类型
     */
    private Integer matchType;

    /**
     * 豁免词
     */
    private String exceptionWords;

    /**
     * 词汇来源
     */
    private String source;

    /**
     * 状态
     */
    private Boolean status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Boolean deleted;
}