package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * L2 风险小类判定规则响应对象。
 *
 * <p>规则用于描述 riskDetailsId 这个粒度上的判定边界。
 * L2 命中多个特征后会按风险小类聚合，后台查看规则时需要同时看到大类、小类、严重等级和正反例。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L2RiskDetailRuleVO {

    /** 主键 ID，对应 risk_detail_rule.id。 */
    private Long id;

    /** 风险小类 ID，对应 risk_details.id；每个小类最多维护一条有效规则。 */
    private Long riskDetailsId;

    /** 风险小类名称，用于后台展示和 L2 日志可读性。 */
    private String detailsName;

    /** 风险大类 ID，对应 risk_category.id。 */
    private Long categoryId;

    /** 风险大类名称，用于后台展示。 */
    private String categoryName;

    /** 小类判定规则文本，描述该风险小类的违规判定口径。 */
    private String judgeRule;

    /** 严重等级：1-低，2-中，3-高，4-致命。 */
    private Integer severityLevel;

    /** 判定边界说明，用于解释违规与合规内容之间的灰度区。 */
    private String decisionBoundary;

    /** 违规正例样本 JSON 字符串，用于辅助审核和策略调试。 */
    private String unsafeExamples;

    /** 安全反例样本 JSON 字符串，用于标注科普、拒答、合规表达等安全边界。 */
    private String safeExamples;

    /** 规则版本；创建为 1，每次编辑、启停或删除事件递增。 */
    private Integer version;

    /** 业务状态：0-禁用，1-启用。 */
    private Integer status;

    /** 创建人。 */
    private String creator;

    /** 更新人。 */
    private String updater;

    /** 创建时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 更新时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
