package com.kant.llm.common.convention;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
     * 一级致命拦截关键词实体列表。
     */
    List<BlockKeyWordEntity> blockKeyWords;

    /**
     * 风险小类ID。
     */
    private Long riskDetailsId;

    /**
     * 一级致命拦截关键词实体。
     * @param keyword 一级致命拦截关键词。
     * @param start 命中关键词的起始索引。
     * @param end 命中关键词的结束索引。
     */
    public record BlockKeyWordEntity(String keyword, int start, int end) {
    }

}


