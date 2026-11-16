package com.kant.llm.eval.simple.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 不可变专家种子及来源，作为生成素材而非自动生成的正式资产。
 * 原文与SHA-256同时保留；每条种子当前固定版本1，修订须另建seedId，不覆盖原文。
 * 对应agent_sample_seed表，不向外开放通用覆盖或删除接口；数据库时间约定为UTC微秒精度。
 */
@Getter
@Setter
@TableName("agent_sample_seed")
public class AgentSampleSeedDO {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 不可变专家种子标识。 */
    private String seedId;
    /** 操作者内创建幂等键。 */
    private String idempotencyKey;
    /** 请求摘要。 */
    private String requestHash;
    /** 种子展示名。 */
    private String name;
    /** 受限种子原文，仅授权详情读取。 */
    private String content;
    /** 原文SHA256。 */
    private String contentHash;
    /** 专家案例来源引用，不自动抓取外部URL。 */
    private String sourceRef;
    /** 状态: 0-停用, 1-启用 */
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建人 */
    private String creator;

    /** 更新人 */
    private String updater;

    /** 逻辑删除标识: 0-未删除, 1-已删除 */
    @TableLogic
    private Boolean deleted;
}
