package com.kant.llm.common.convention;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L1EvalContext {

    /**
     * 原始用户 Prompt。
     */
    private String originalPrompt;

    /**
     * 是否在 L1 命中一级致命拦截词。
     */
    private boolean l1Blocked;

    /**
     * 触发 L1 致命拦截的字面量关键词。
     */
    private String l1BlockKeyword;

    /**
     * 命中关键词的起始索引。
     */
    private int start;

    /**
     * 命中关键词的结束索引。
     */
    private int end;

    /**
     * 风险小类ID。
     */
    private Long riskDetailsId;
}
