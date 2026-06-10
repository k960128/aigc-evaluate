package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L2 风险小类判定规则保存请求。
 *
 * <p>创建时 id 为空；更新时 id 必填。
 * 服务层会限制每个 riskDetailsId 只有一条有效规则，确保 L2 聚合到风险小类后能拿到唯一判定边界。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveL2RiskDetailRuleRequest {

    /** 主键 ID，创建时为空，更新时必填。 */
    private Long id;

    /** 风险小类 ID，对应 risk_details.id；创建时必填，更新时可不传。 */
    private Long riskDetailsId;

    /** 小类判定规则文本；描述什么情况下应判为该风险小类，创建时必填。 */
    private String judgeRule;

    /** 严重等级：1-低，2-中，3-高，4-致命；用于后续路由和人工审核优先级。 */
    private Integer severityLevel;

    /** 判定边界说明；用于写清楚违规与安全内容之间的灰度边界。 */
    private String decisionBoundary;

    /** 违规正例样本 JSON 字符串；只保存识别样例，不应写入可操作危险步骤。 */
    private String unsafeExamples;

    /** 安全反例样本 JSON 字符串；用于标注科普、拒答、合规表达等安全边界。 */
    private String safeExamples;

    /** 业务状态：0-禁用，1-启用；未传时创建默认启用。 */
    private Integer status;

    /** 创建人；仅在创建或显式传入时写入。 */
    private String creator;

    /** 更新人；用于后台审计和问题追踪。 */
    private String updater;
}
