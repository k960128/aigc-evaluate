package com.kant.llm.eval.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库样本和评测题目关联关系表
 */
@Data
@TableName("knowledge_document_sample_relation")
public class KnowledgeDocumentSampleRelationDO {

    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 样本文档ID指向knowledge_feature_document.id
     */
    private Long documentId;

    /**
     * 评测题目ID指向dataset_sample.id
     */
    private Long sampleId;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 更新人
     */
    private String updater;

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
     * 逻辑删除：0-未删除，1-已删除
     */
    @TableLogic
    private Boolean deleted;
}