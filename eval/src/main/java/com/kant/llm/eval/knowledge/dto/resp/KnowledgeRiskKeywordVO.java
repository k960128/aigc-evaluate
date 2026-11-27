package com.kant.llm.eval.knowledge.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 风险词库特征词详情。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRiskKeywordVO {

    /** 特征词 ID。 */
    private Long id;

    /** 风险明细 ID，对应 risk_details.id。 */
    private Long riskDetailsId;

    /** 字面量特征词。 */
    private String keyword;

    /** 匹配模式:   1-精确匹配,   2-正则匹配 */
    private Integer matchType;

    /** 创建人。 */
    private String creator;

    /** 更新人。 */
    private String updater;

    /** 创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 逻辑删除：false-未删除，true-已删除。 */
    private Boolean deleted;
}
