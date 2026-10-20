package com.kant.llm.eval.sample.dto;

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

    /** 执行状态，具体取值待定 */
    private String status;

    /** 已进行的执行/尝试次数 */
    private Integer attemptCount;

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
