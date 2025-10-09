package com.chinatelecom.aigc.evaluate.common.enums;

/**
 * 题目难度枚举
 */
public enum QuestionDifficultyEnum {

    SIMPLE("SIMPLE","简单"),
    MEDIUM("MEDIUM","中等"),
    HARD("HARD","困难");


    private String code;
    private String desc;

    QuestionDifficultyEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // 根据描述获取code
    public static String getCodeByDesc(String desc) {
        for (QuestionDifficultyEnum value : QuestionDifficultyEnum.values()) {
            if (value.getDesc().equals(desc)) {
                return value.getCode();
            }
        }
        return "NULL";
    }

    public String getDesc() {
        return desc;
    }

    public String getCode() {
        return code;
    }
}
