package com.kant.llm.eval.knowledge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 攻击语义知识库版本快照表
 */
@Data
@Builder
@TableName("knowledge_feature_document_version_snapshot")
public class KnowledgeFeatureDocumentVersionSnapshotDO {

    /**
     * 版本ID
     */
    @TableId(type = IdType.AUTO)
    private Long versionId;

    /**
     * 前一个版本ID
     */
    private Long beforeVersionId;

    /**
     * 版本号（语义化版本，如 1|2|3）
     */
    private Integer version;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer lockVersion;

    /**
     * 版本状态：INIT, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED, STORED
     */
    private String status;

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