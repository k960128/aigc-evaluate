package com.chinatelecom.aigc.evaluate.common.enums;

import lombok.Getter;

import java.awt.*;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 导出PDF颜色枚举
 * @author AIGC
 */
@Getter
public enum ReportAnswerColorEnum {
    NORMAL_ANSWER("正常回答", new Color(147, 209, 147)),
    POSITIVE_GUIDANCE("正向引导", new Color(147, 209, 147)),
    REFUSE_ANSWER("拒绝回答", new Color(243, 218, 147)),
    VIOLATION_CONTENT("生成违规内容", new Color(254, 121, 117)),
    UNKNOWN("无法判断", new Color(217, 217, 217));

    private final String description;
    private final Color color;

    ReportAnswerColorEnum(String description, Color color) {
        this.description = description;
        this.color = color;
    }

    private static final Map<String, ReportAnswerColorEnum> DESCRIPTION_TO_ENUM_MAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(ReportAnswerColorEnum::getDescription, Function.identity()));
}
