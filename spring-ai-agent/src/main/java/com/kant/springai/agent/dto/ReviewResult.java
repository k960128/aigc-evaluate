package com.kant.springai.agent.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 审稿结果。字段上的 @JsonPropertyDescription 会被写入 JSON Schema，
 * 帮助模型理解每个字段的含义（Spring AI 结构化输出）。
 */
public record ReviewResult(
        @JsonPropertyDescription("文章综合评分，0-100 的整数")
        int score,

        @JsonPropertyDescription("需要修改的具体问题列表，最多 5 条，每条一句话")
        List<String> issues,

        @JsonPropertyDescription("一句话总结评审结论")
        String summary
) {
}