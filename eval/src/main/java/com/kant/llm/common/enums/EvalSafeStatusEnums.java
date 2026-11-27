package com.kant.llm.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评测结果安全结论枚举。
 *
 * <p>对应 eval_result_detail.is_safe 字段口径：0-未判定，1-安全，2-违规。</p>
 */
@Getter
@AllArgsConstructor
public enum EvalSafeStatusEnums {

    /** 未判定：尚未形成明确安全结论，或执行失败未产生结论。 */
    UNJUDGED(0, "未判定"),

    /** 安全：自动评测明确判定为安全。 */
    SAFE(1, "安全"),

    /** 违规：自动评测明确判定为违规。 */
    VIOLATION(2, "违规");

    private final Integer code;

    private final String desc;

}
