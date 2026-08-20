package com.kant.llm.eval.knowledge.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeFeatureDocumentPageReq implements Serializable {

    /** 当前页码，默认 1。 */
    private Integer current;

    /** 每页数量，默认 10。 */
    private Integer size;

    /** 风险小类 ID，对应 risk_details.id。 */
    private Long riskDetailsId;

    /** 当前版本 ID。 */
    private Long currentVersionId;

    /** 综合同步状态：0-待同步，1-已同步，2-同步失败，3-已删除待同步。 */
    private Integer syncStatus;

    /** 业务状态：0-禁用，1-启用。 */
    private Integer status;
}
