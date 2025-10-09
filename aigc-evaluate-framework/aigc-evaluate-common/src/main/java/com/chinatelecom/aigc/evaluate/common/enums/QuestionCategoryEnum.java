package com.chinatelecom.aigc.evaluate.common.enums;

public enum QuestionCategoryEnum {

    //-正向题库
    FORWARD("FORWARD", "正向题库"),
    //-负向题库
    NEGATIVE("NEGATIVE", "负向题库");

    private String code;
    private String desc;

    QuestionCategoryEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // 根据描述获取code
    public static String getCodeByDesc(String desc) {
        for (QuestionCategoryEnum value : QuestionCategoryEnum.values()) {
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
