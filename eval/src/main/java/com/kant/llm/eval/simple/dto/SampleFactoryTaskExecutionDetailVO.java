package com.kant.llm.eval.simple.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 样本扩充任务执行明细响应对象
 */
@Data
public class SampleFactoryTaskExecutionDetailVO {

    /** 主键ID */
    private Long id;

    /** 样本生产任务ID */
    private String taskId;

    /** 幂等号 */
    private String idempotencyKey;

    /** 执行状态：RUNNING-执行中，SUCCESS-评审通过，FAILED-评审未通过或执行异常 */
    private String status;

    /** 已进行的执行/尝试次数 */
    private Integer attemptCount;

    /** 异常信息 */
    private String errorMsg;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 创建人 */
    private String creator;

    /** 更新人 */
    private String updater;
}
