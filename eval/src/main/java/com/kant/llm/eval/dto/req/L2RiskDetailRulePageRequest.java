package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L2 风险小类判定规则分页查询请求。
 *
 * <p>风险小类规则用于描述某个 risk_details 的判定边界、正反例和严重等级。
 * L2 运行时按 riskDetailsId 聚合命中，因此规则也以风险小类为唯一维护粒度。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L2RiskDetailRulePageRequest {

    /** 当前页码，默认 1；传空或小于等于 0 时由服务层兜底。 */
    private Integer current;

    /** 每页数量，默认 10；传空或小于等于 0 时由服务层兜底。 */
    private Integer size;

    /** 风险大类 ID，用于通过 risk_details 反查该大类下的所有规则。 */
    private Long categoryId;

    /** 风险小类 ID，对应 risk_details.id。 */
    private Long riskDetailsId;

    /** 规则启停状态：0-禁用，1-启用。 */
    private Integer status;

    /** 严重等级：1-低，2-中，3-高，4-致命；用于筛选高优先级风险规则。 */
    private Integer severityLevel;
}
