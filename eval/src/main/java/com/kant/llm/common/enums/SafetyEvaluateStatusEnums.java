package com.kant.llm.common.enums;

import lombok.Getter;

/**
 * 安全评估状态枚举
 * 定义安全评估执行器的执行状态，包括安全、违规、模糊区等。
 */
@Getter
public enum SafetyEvaluateStatusEnums {

    /**
     * 安全
     */
    SAFE("SAFE", "安全"),
    /**
     * 违规
     */
    VIOLATION("VIOLATION", "违规"),
    /**
     * 模糊区
     */
    BLURRY("BLURRY", "模糊区"),
    /**
     * 未知
     */
    UNKNOWN("UNKNOWN", "未知"),

    /**
     * 空召回
     */
    EMPTY_RECALL("EMPTY_RECALL", "空召回"),
    ;


    private final String code;
    private final String desc;

    SafetyEvaluateStatusEnums(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }


    /**
     * L1层 安全评估状态枚举
     */
    @Getter
    enum L1SafetyEvaluateStatusEnums {
        /**
         * 命中高危规则
         */
        RULE_HIT_HIGH("RULE_HIT_HIGH", "命中高危规则"),
        /**
         * 命中但置信度不足，需 L2/L3 复核
         */
        RULE_HIT_SUSPECT("RULE_HIT_SUSPECT", "命中但置信度不足，需 L2/L3 复核"),
        /**
         * 未命中任何规则
         */
        RULE_MISS("RULE_MISS", "未命中任何规则"),
        ;


        private final String code;
        private final String desc;

        L1SafetyEvaluateStatusEnums(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        }



    /**
     * L2层 安全评估状态枚举
     */
    @Getter
    enum L2SafetyEvaluateStatusEnums {
        /**
         * 高相似度
         */
        SEMANTIC_HIT("SEMANTIC_HIT", "高相似度"),
        /**
         * 相似度在灰区
         */
        SEMANTIC_SUSPECT("SEMANTIC_SUSPECT", "相似度在灰区"),
        /**
         * 未命中,空召回
         */
        SEMANTIC_MISS("SEMANTIC_MISS", "未命中"),
        ;


        private final String code;
        private final String desc;

        L2SafetyEvaluateStatusEnums(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }

    /**
     * L3层 安全评估状态枚举
     */
    @Getter
    enum L3SafetyEvaluateStatusEnums {
        /**
         * 高相似度
         */
        SEMANTIC_HIT("SEMANTIC_HIT", "高相似度"),
        /**
         * 相似度在灰区
         */
        SEMANTIC_SUSPECT("SEMANTIC_SUSPECT", "相似度在灰区"),
        /**
         * 未命中,空召回
         */
        SEMANTIC_MISS("SEMANTIC_MISS", "未命中"),
        ;


        private final String code;
        private final String desc;

        L3SafetyEvaluateStatusEnums(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }
}
