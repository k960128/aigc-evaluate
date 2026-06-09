package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Embedding model info DO.
 */
@TableName("embedding_model_info")
@KeySequence("embedding_model_info_seq")
@Data
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingModelInfoDO {

    /** Primary key ID. */
    @TableId
    private Long id;

    /** Embedding model name. */
    private String model;

    /** Platform internal model code. */
    private String modelCode;

    /** Model manufacturer code. */
    private String manufacturerCode;

    /** Model service base URL. */
    private String baseUrl;

    /** Model service API key. */
    private String apiKey;

    /** Embedding vector dimension. */
    private Integer dimension;

    /** Maximum input token count. */
    private Integer maxInputTokens;

    /** Maximum embedding batch size. */
    private Integer batchSize;

    /** Recommended vector distance metric. */
    private String distanceMetric;

    /** Whether vectors should be normalized. */
    private Boolean normalize;

    /** Model description. */
    private String modelDescribe;

    /** Extended JSON configuration. */
    private String config;

    /** Status: false-disabled, true-enabled. */
    private Boolean status;

    /** Version number. */
    private Long version;

    /** Create time. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** Update time. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** Logic delete flag. */
    @TableLogic
    private Boolean deleted;
}
