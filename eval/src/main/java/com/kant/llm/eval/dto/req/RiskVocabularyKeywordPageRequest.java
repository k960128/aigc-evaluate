package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskVocabularyKeywordPageRequest {
    /** 当前页码，默认 1 */
    private Integer current;

    /** 每页数量，默认 10 */
    private Integer size;

    /** 所属风险明细 ID */
    private Long riskDetailsId;

    /** 所属分组 ID */
    private Long groupId;

    /** 字面量特征词，支持模糊查询 */
    private String keyword;

    /** 风险等级：1-致命级别，2-疑似级别 */
    private Integer riskLevel;

    /** 匹配模式：1-精确匹配，2-模糊包含匹配 */
    private Integer matchType;

    /** Redis 同步状态：0-待同步，1-已同步 */
    private Integer syncStatus;
}
