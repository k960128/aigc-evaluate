package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRiskVocabularyGroupRequest {
    /** 主键 ID */
    private Long id;

    /** 分组名称 */
    private String name;

    /** 分组描述 */
    private String description;

    /** 更新人 */
    private String updater;
}
