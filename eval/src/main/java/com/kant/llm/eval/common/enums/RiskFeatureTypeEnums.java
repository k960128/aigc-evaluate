package com.kant.llm.eval.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * L2 攻击特征类型枚举。
 *
 * <p>该枚举描述 risk_attack_feature 表中一条知识特征的来源形态，
 * 后续 ES/Milvus 召回和 Reranker 精排会根据类型做权重调整。</p>
 */
@Getter
@AllArgsConstructor
public enum RiskFeatureTypeEnums {

    /** 明确风险关键词，通常用于字面匹配。 */
    KEYWORD("KEYWORD", "风险关键词"),

    /** 已知攻击 payload，命中后通常具备较高解释性。 */
    PAYLOAD("PAYLOAD", "攻击Payload"),

    /** 诱导 Prompt 模板，例如角色扮演、越权指令。 */
    PROMPT_PATTERN("PROMPT_PATTERN", "Prompt诱导模板"),

    /** 风险输出片段，用于匹配被测模型的危险回复。 */
    RESPONSE_PATTERN("RESPONSE_PATTERN", "风险输出模式"),

    /** 越狱话术或绕过安全策略的表达。 */
    JAILBREAK("JAILBREAK", "越狱话术"),

    /** 语义相似案例，用于向量召回泛化。 */
    SIMILAR_CASE("SIMILAR_CASE", "相似风险案例"),

    /** 安全例外样本，例如拒答、安全科普、合规解释。 */
    SAFE_EXCEPTION("SAFE_EXCEPTION", "安全例外样本");

    private final String code;

    private final String desc;
}
