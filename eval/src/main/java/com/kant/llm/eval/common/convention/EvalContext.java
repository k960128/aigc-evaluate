package com.kant.llm.eval.common.convention;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 贯穿 L1、L2 以及后续评测阶段的评测上下文。
 *
 * <p>每个请求拥有一个独立上下文实例。L1 极速拦截引擎只写入轻量级路由数据：
 * 致命拦截信息，以及由二级风险词命中收集到的风险明细 ID。</p>
 */
@Data
@NoArgsConstructor
public class EvalContext {

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
     * 触发 L1 致命拦截的风险明细 ID。
     */
    private Long l1BlockDetailsId;

    /**
     * 二级风险词命中后收集到的风险明细 ID 列表。
     */
    private List<Long> hitWarningTags = new ArrayList<>();

    public EvalContext(String originalPrompt) {
        this.originalPrompt = originalPrompt;
    }

    /**
     * 标记当前上下文已被 L1 致命拦截。
     *
     * @param riskDetailsId 命中的风险明细 ID
     * @param keyword 命中的字面量关键词
     */
    public void markL1Blocked(Long riskDetailsId, String keyword) {
        this.l1Blocked = true;
        this.l1BlockDetailsId = riskDetailsId;
        this.l1BlockKeyword = keyword;
    }

    /**
     * 添加疑似风险标签，并完成空值过滤和去重。
     *
     * @param riskDetailsId 命中的风险明细 ID
     */
    public void addWarningTag(Long riskDetailsId) {
        if (riskDetailsId == null) {
            return;
        }
        if (hitWarningTags == null) {
            hitWarningTags = new ArrayList<>();
        }
        if (!hitWarningTags.contains(riskDetailsId)) {
            hitWarningTags.add(riskDetailsId);
        }
    }
}
