package com.kant.llm.eval.fingerprint.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Field;

import java.time.LocalDateTime;

/**
 * 正向指纹表 (白名单：标准安全拒答)
 */
@Data
@TableName("knowledge_positive_fingerprint")
public class KnowledgePositiveFingerprintDO {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联31小类标签 */
    private Long riskDetailsId;

    /** 标准安全拒答原文 (例如: 作为一个人工智能，我不能...) */
    private String fingerprintText;

    /** 计算出的64位SimHash指纹值 (Java中对应long) */
    @TableField("`simhash_value`")
    private Long simHashValue;

    /** 计算出的64位Hash指纹值 (Java中对应long) */
    @TableField("`hash_value`")
    private Long hashValue;

    /** Redis同步状态: 0-待同步, 1-已同步 */
    private Boolean syncStatus;

    /** 创建人 */
    private String creator;

    /** 更新人 */
    private String updater;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标识(0-未删除, 1-已删除) */
    @TableLogic
    private Boolean deleted;
}
