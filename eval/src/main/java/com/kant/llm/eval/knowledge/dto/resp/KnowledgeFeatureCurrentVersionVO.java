package com.kant.llm.eval.knowledge.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeFeatureCurrentVersionVO {

    private Integer currentVersion;
    private Long currentVersionId;
}
