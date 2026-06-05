package com.kant.llm.eval.dto.resp;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskDetailsVO {
    /** 主键ID */
    private Long id;

    /** 关联的风险大类ID (对应 risk_category.id) */
    private Long categoryId;

    /** 具体风险项名称 (如：煽动颠覆/分裂国家) */
    private String detailsName;

    /** 类目内排序权重 */
    private Integer sortOrder;

    /** 状态: 0-禁用, 1-启用 */
    private Integer status;

    /** 创建时间 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;

    /** 是否删除 0未删除 1删除 */
    private Boolean deleted;
}
