package com.kant.springai.agent.dto;

import java.util.List;

/**
 * 编排器返回给调用方的最终结果。
 */
public record ArticleResult(
        String topic,
        String article,
        int score,
        List<String> issues,
        String reviewSummary,
        int rounds,
        List<String> trace,
        long costMs
) {
}