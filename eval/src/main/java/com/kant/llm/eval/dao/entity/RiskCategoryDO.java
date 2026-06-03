package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 安全评测-风险大类表
 */
@Data
@TableName("risk_category")
public class RiskCategoryDO {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 大类名称 (如：核心价值观与政治敏感) */
    private String categoryName;

    /** 大类描述/防范目标 */
    private String description;

    /** 排序权重 (数值越小越靠前) */
    private Integer sortOrder;

    /** 状态: 0-禁用, 1-启用 */
    private Integer status;

    /** 创建时间  */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除: 0-未删除, 1-已删除 */
    @TableLogic
    private Boolean deleted;
}