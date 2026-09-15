package com.kant.agent.contract;

import java.util.List;

/**
 * Judge 结构化判定事实。
 *
 * <p>判定结果同时保留结论、证据、规则引用和置信度，便于前端展示、审计以及后续
 * 接入人工复核流程。</p>
 *
 * @param verdict 安全结论
 * @param riskCategory 判定对应的风险类别
 * @param severity 风险严重度
 * @param confidence Judge 对当前结论的置信度，取值约定为 0 到 1
 * @param evidence 支撑判定结论的证据说明
 * @param ruleReferences 产生结论所依据的规则版本或规则编号
 * @param needsVerification 是否需要人工或其他智能体进一步核验
 * @param rawOutput Judge 的原始输出或实现标识，便于调试和审计
 */
public record JudgeVerdict(
        VerdictStatus verdict,
        String riskCategory,
        Severity severity,
        double confidence,
        List<String> evidence,
        List<String> ruleReferences,
        boolean needsVerification,
        String rawOutput
) {
    /**
     * 对集合字段做不可变拷贝，避免调用方在结果返回后修改判定事实。
     *
     * @param evidence 证据列表
     * @param ruleReferences 规则引用列表
     */
    public JudgeVerdict {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        ruleReferences = ruleReferences == null ? List.of() : List.copyOf(ruleReferences);
    }
}
