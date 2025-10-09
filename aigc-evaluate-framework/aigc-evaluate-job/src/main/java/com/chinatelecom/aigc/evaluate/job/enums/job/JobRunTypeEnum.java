package com.chinatelecom.aigc.evaluate.job.enums.job;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务类型枚举
 *
 * @author 后端源码
 */
@Getter
@AllArgsConstructor
public enum JobRunTypeEnum {
    MANUAL(0),    // 手动执行任务
    ONE_TIME(1),  // 一次性任务
    SCHEDULED(2); // 周期性任务

    /**
     * 状态
     */
    private final Integer status;

}
