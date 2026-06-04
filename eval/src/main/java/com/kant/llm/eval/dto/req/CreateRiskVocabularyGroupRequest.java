package com.kant.llm.eval.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRiskVocabularyGroupRequest {
    /** 分组名称 */
    private String name;

    /** 分组描述 */
    private String description;

    /** 创建人 */
    private String creator;
}
