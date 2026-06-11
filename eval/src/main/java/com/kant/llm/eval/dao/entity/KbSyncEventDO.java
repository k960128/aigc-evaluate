package com.kant.llm.eval.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库索引同步事件 DO。
 *
 * <p>该表记录 MySQL 知识事实同步到 ES/Milvus 的处理状态，便于后续 MQ 重试和对账补偿。</p>
 */
@Data
@TableName("kb_sync_event")
public class KbSyncEventDO {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件唯一 ID，用于 MQ 消费幂等。 */
    private String eventId;

    /** 聚合类型：ATTACK_FEATURE、DETAIL_RULE。 */
    private String aggregateType;

    /** 聚合 ID，例如 risk_attack_feature.id 或 risk_detail_rule.id。 */
    private Long aggregateId;

    /** 操作类型：CREATE、UPDATE、DELETE、REINDEX。 */
    private String operationType;

    /** 风险小类 ID，用于按小类追踪同步状态。 */
    private Long riskDetailsId;

    /** 内容 hash，用于校验同步事件是否对应当前内容。 */
    private String contentHash;

    /** 事件对应的数据版本。 */
    private Integer version;

    /** 事件载荷快照，JSON 字符串。 */
    private String payload;

    /** ES 处理状态：0-待处理，1-成功，2-失败。 */
    private Integer esStatus;

    /** Milvus 处理状态：0-待处理，1-成功，2-失败。 */
    private Integer milvusStatus;

    /** PG 处理状态：0-待处理，1-成功，2-失败。 */
    private Integer pgStatus;

    /** 重试次数。 */
    private Integer retryCount;

    /** 下次重试时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextRetryTime;

    /** 最近一次失败原因。 */
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
