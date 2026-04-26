package com.kant.llm.eval.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态枚举
 */
@Getter
@AllArgsConstructor
public enum TaskStatusEnums {

    CREATING(0, "创建中"),
    INITIALIZING(1, "初始化中"),
    READY(2, "就绪"),
    RUNNING(3, "进行中"),
    COMPLETED(4, "已完成"),
    ERROR(5, "异常"),
    STOPPED(6, "已停止");

    private final Integer code;
    private final String desc;

}
