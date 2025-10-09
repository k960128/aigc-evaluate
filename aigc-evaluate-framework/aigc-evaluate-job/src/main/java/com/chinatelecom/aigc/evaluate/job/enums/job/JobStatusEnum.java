package com.chinatelecom.aigc.evaluate.job.enums.job;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.quartz.impl.jdbcjobstore.Constants;

import java.util.Collections;
import java.util.Set;

/**
 * 任务状态的枚举
 *
 * @author 后端源码
 */
@Getter
@AllArgsConstructor
public enum JobStatusEnum {

    /**
     * 初始化中
     */
    INIT(0, Collections.emptySet()),
    /**
     * 等待执行
     */
    NORMAL(1, Sets.newHashSet(Constants.STATE_WAITING, Constants.STATE_ACQUIRED, Constants.STATE_BLOCKED)),
    /**
     * 执行中
     */
    EXECUTING(2, Sets.newHashSet(Constants.STATE_EXECUTING)),
    /**
     * 暂停执行
     */
    PAUSED(3, Sets.newHashSet(Constants.STATE_COMPLETE)),
    /**
     * 执行完成
     */
    COMPLETE(4, Sets.newHashSet(Constants.STATE_COMPLETE)),
    /**
     * 执行失败
     */
    FAILED(5, Sets.newHashSet(Constants.STATE_ERROR));
    /**
     * 状态
     */
    private final Integer status;
    /**
     * 对应的 Quartz 触发器的状态集合
     */
    private final Set<String> quartzStates;


    // 根据状态码获取对应的 JobStatusEnum
    public static JobStatusEnum fromStatus(int status) {
        for (JobStatusEnum jobStatus : values()) {
            if (jobStatus.getStatus() == status) {
                return jobStatus;
            }
        }
        throw new IllegalArgumentException("未知的任务状态: " + status);  // 或者返回一个默认值
    }

}
