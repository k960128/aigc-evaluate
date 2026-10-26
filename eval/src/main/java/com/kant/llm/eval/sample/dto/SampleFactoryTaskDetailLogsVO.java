package com.kant.llm.eval.sample.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 样本扩充任务详情执行日志响应对象。
 */
@Data
public class SampleFactoryTaskDetailLogsVO {

    /** 主键ID */
    private Long id;

    /** 样本生产任务ID */
    private String taskId;

    /** 样本生产批次任务ID */
    private Long taskDetailId;

    /** 消息唯一标识 */
    private String messageId;

    /** 当前迭代轮次，从1开始 */
    private Integer currentRound;

    /** Agent角色：GENERATOR_AGENT/JUDGE_AGENT */
    private String type;

    /** 请求提示词 */
    private String promptContent;

    /** 返回消息内容 */
    private String contentResponse;

    /** 执行状态：RUNNING/ SUCCESS/ FAILED */
    private String status;

    /** Token数量 */
    private Integer tokenCount;

    /** 节点耗时，单位毫秒 */
    private Integer latency;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
