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
     * 版本ID
     */
    private Long versionId;

    /**
     * 上一个版本ID
     */
    private Long beforeVersionId;
}
