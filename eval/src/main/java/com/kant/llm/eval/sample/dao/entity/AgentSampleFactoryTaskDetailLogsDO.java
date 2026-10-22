package com.kant.llm.eval.sample.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 样本扩充-任务详情执行批次详情日志 DO
 */
@Data
@TableName("agent_sample_factory_task_detail_logs")
public class AgentSampleFactoryTaskDetailLogsDO {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样本生产任务ID，关联 agent_sample_factory_task.task_id */
    private String taskId;

    /** 样本生产批次任务ID，关联 agent_sample_factory_task_execution_detail.id */
    private Long taskDetailId;

    /** 消息唯一标识 */
    private String messageId;

    /** 角色：GENERATOR_AGENT/JUDGE_AGENT */
    private String type;

    /** 请求提示词 */
    private String promptContent;

    /** 返回消息内容 */
    private String contentResponse;

    /** 执行状态：RUNNING-执行中，SUCCESS-成功，FAILED-失败 */
    private String status;

    /** Token数量 */
    private Integer tokenCount;

    /** 节点耗时，单位毫秒 */
    private Integer latency;

    /** 乐观锁版本号 */
    @Version
    private Integer lockVersion;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 是否删除：0-未删除，1-已删除 */
    @TableLogic
    private Boolean deleted;
}
