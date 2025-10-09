package com.chinatelecom.aigc.evaluate.domain;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.dataobject.BaseDO;
import com.chinatelecom.aigc.evaluate.job.enums.job.JobStatusEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 定时任务 DO
 *
 * @author 后端源码
 */
@TableName("job_info")
@KeySequence("job_info_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDO extends BaseDO {

    /**
     * 任务编号
     */
    @TableId
    private Long id;
    /**
     * 任务名称
     */
    private String name;
    /**
     * 任务名称
     */
    private String description;
    /**
     * 任务状态
     *
     * 枚举 {@link JobStatusEnum}
     */
    private Integer status;
    /**
     * 处理器的名字
     */
    private String handlerName;
    /**
     * 处理器的参数
     */
    private String handlerParam;
    /**
     * 最大线程数量
     */
    private Integer maxThreadSize;
    /**
     * CRON 表达式
     */
    private String cronExpression;
    /**
     * 一次性执行
     */
    private String oneTimeExpression;
    /**
     * 调度类型 0:手动执行任务 1:一次性任务  2:周期性
     */
    private Integer runType;

    /**
     * 重试次数
     * 如果不重试，则设置为 0
     */
    private Integer retryCount;
    /**
     * 重试间隔，单位：毫秒
     * 如果没有间隔，则设置为 0
     */
    private Integer retryInterval;

    // ========== 监控相关字段 ==========
    /**
     * 监控超时时间，单位：毫秒
     * 为空时，表示不监控
     *
     * 注意，这里的超时的目的，不是进行任务的取消，而是告警任务的执行时间过长
     */
    private Integer monitorTimeout;

    /** 开始时间  */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
