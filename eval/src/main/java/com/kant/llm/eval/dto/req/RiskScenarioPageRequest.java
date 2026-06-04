package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskScenarioPageRequest {
    /** 当前页码，默认 1 */
    private Integer current;

    /** 每页数量，默认 10 */
    private Integer size;

    /** 直属大类 ID */
    private Long majorCategoryId;

    /** 搜索关键词，支持匹配场景名称和场景编码 */
    private String keyword;

    /** 严重等级：1-低，2-中，3-高，4-致命 */
    private Integer severityLevel;

    /** 状态：0-停用，1-启用 */
    private Integer status;
}
