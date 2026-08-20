package com.kant.llm.eval.knowledge.mq.v2.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPublishVersionMessage {

    /**
     * 风险详情ID
     */
    private Long riskDetailsId;

    /**
     * 版本ID
     */
    private Long versionId;
}
