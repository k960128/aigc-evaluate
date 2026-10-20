package com.kant.llm.eval.risk.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 风险大类及其下属风险小类
 */
@Data
public class RiskCategoryTreeVO {

    /** 风险大类ID */
    private Long id;

    /** 风险大类名称 */
    private String categoryName;

    /** 风险大类描述或防范目标 */
    private String description;

    /** 排序权重，数值越小越靠前 */
    private Integer sortOrder;

    /** 状态: 0-禁用, 1-启用 */
    private Integer status;

    /** 创建时间 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;

    /** 逻辑删除标识 */
    private Boolean deleted;

    /** 下属风险小类，无小类时返回空数组 */
    private List<RiskDetailsVO> children = new ArrayList<>();
}
