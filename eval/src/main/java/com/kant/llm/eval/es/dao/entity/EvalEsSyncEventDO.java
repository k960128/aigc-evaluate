package com.kant.llm.eval.es.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评测 ES 投影同步 outbox 事件 DO。
 *
 * <p>业务表仍以 MySQL 为唯一事实源。每次结果明细或流水线节点日志发生可见状态变化时，
 * 同步写入一条 outbox 事件，后续由 MQ 消费者回查 MySQL 最新行并写入 ES。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("eval_es_sync_event")
public class EvalEsSyncEventDO {

    /** 事件唯一 ID，使用雪花 ID 字符串，作为 MQ 消费与补偿幂等标识。 */
    @TableId
    private String eventId;

    /** 聚合类型：RESULT_DETAIL、PIPELINE_NODE。 */
    private String aggregateType;

    /** 聚合主键 ID，对应 eval_result_detail.id 或 eval_pipeline_node_detail.id。 */
    private Long aggregateId;

    /** 操作类型：UPSERT，DELETE 预留。 */
    private String operationType;

    /** 评测任务 ID，冗余保存便于排查同步链路。 */
    private Long taskId;

    /** 任务执行批次 ID，冗余保存便于按批次补偿或排查。 */
    private Long taskDetailId;

    /** 评测结果明细 ID，节点日志事件指向所属结果明细。 */
    private Long resultDetailId;

    /** 数据集样本 ID，冗余保存便于定位单条样本。 */
    private Long sampleId;

    /** 同步状态：0-待同步，1-已同步，2-同步失败。 */
    private Integer syncStatus;

    /** 重试次数。 */
    private Integer retryCount;

    /** 下次允许调度重试时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextRetryTime;

    /** 最近一次失败原因，写入前会做长度保护。 */
    private String lastError;

    /** 创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
