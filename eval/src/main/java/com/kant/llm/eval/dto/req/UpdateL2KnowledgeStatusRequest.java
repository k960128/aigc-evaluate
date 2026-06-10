package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L2 知识库规则或特征启停请求。
 *
 * <p>规则和特征共用该请求对象。
 * 启停本质上会影响 L2 判定或召回可见性，因此服务层会生成 REINDEX 同步事件。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateL2KnowledgeStatusRequest {

    /** 主键 ID，对应 risk_detail_rule.id 或 risk_attack_feature.id。 */
    private Long id;

    /** 业务状态：0-禁用，1-启用。 */
    private Integer status;

    /** 更新人，用于后台审计和问题追踪。 */
    private String updater;
}
