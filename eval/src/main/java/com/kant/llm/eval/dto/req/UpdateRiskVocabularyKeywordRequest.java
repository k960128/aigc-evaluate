package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRiskVocabularyKeywordRequest {
    /** 主键 ID */
    private Long id;

    /** 所属分组 ID */
    private Long groupId;

    /** 所属风险明细 ID */
    private Long riskDetailsId;

    /** 字面量特征词 */
    private String keyword;

    /** 风险等级：1-致命级别，2-疑似级别 */
    private Integer riskLevel;

    /** 匹配模式：1-精确匹配，2-模糊包含匹配 */
    private Integer matchType;

    /** Redis 同步状态：0-待同步，1-已同步 */
    private Integer syncStatus;

    /** 更新人 */
    private String updater;
}
