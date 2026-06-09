package com.kant.llm.eval.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 风险严重等级枚举。
 *
 * <p>风险等级用于 L2 聚合打分和短路路由，数值越大表示风险越高。</p>
 */
@Getter
@AllArgsConstructor
public enum RiskSeverityLevelEnums {

    /** 低风险，通常需要结合更多上下文判断。 */
    LOW(1, "低"),

    /** 中风险，默认等级。 */
    MEDIUM(2, "中"),

    /** 高风险，命中强证据时可倾向违规短路。 */
    HIGH(3, "高"),

    /** 致命风险，通常代表不可放行的红线风险。 */
    FATAL(4, "致命");

    private final Integer code;

    private final String desc;
}
