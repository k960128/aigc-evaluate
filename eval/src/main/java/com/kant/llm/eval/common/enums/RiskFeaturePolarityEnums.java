package com.kant.llm.eval.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * L2 攻击特征极性枚举。
 *
 * <p>极性用于区分“提升违规置信度”的风险证据和“降低误报”的安全例外证据。</p>
 */
@Getter
@AllArgsConstructor
public enum RiskFeaturePolarityEnums {

    /** 风险特征，召回后提升对应风险小类的违规置信度。 */
    UNSAFE("UNSAFE", "风险特征"),

    /** 安全例外，召回后用于识别拒答、安全科普、合规讨论等非违规内容。 */
    SAFE_EXCEPTION("SAFE_EXCEPTION", "安全例外");

    private final String code;

    private final String desc;
}
