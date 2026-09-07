package com.kant.llm.common.enums;

/**
 * 安全评估状态枚举
 * 定义安全评估执行器的执行状态，包括安全、违规、模糊区等。
 */
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
    UNKNOWN("UNKNOWN", "未知");


    private String code;
    private String desc;

    SafetyEvaluateStatusEnums(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
