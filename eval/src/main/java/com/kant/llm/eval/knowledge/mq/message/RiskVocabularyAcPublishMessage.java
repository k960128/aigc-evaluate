package com.kant.llm.eval.knowledge.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AC 快照构建和激活广播消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskVocabularyAcPublishMessage {

    public static final String BUILD_VERSION = "BUILD_VERSION";

    public static final String ACTIVATE_VERSION = "ACTIVATE_VERSION";

    private String messageType;

    private Long versionId;

    private String manifestKey;

    private String globalHash;

    private Integer totalWordCount;

    private LocalDateTime publishTime;
}
