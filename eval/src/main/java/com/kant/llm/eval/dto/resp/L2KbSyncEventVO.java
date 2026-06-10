package com.kant.llm.eval.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * L2 知识库同步事件响应对象。
 *
 * <p>用于后台查看知识库变更是否已经被索引侧消费。
 * 一条事件代表一次规则或特征变更，payload 保存当时的数据快照，避免后续源数据变化后丢失同步上下文。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class L2KbSyncEventVO {

    /** 主键 ID，仅用于数据库内部排序和分页。 */
    private Long id;

    /** 同步事件唯一 ID；外部同步器回写状态时使用该字段定位事件。 */
    private String eventId;

    /** 聚合类型：ATTACK_FEATURE-攻击特征，DETAIL_RULE-风险小类规则。 */
    private String aggregateType;

    /** 聚合 ID，对应 risk_attack_feature.id 或 risk_detail_rule.id。 */
    private Long aggregateId;

    /** 操作类型：CREATE-新增，UPDATE-编辑，DELETE-删除，REINDEX-启停后重建索引。 */
    private String operationType;

    /** 风险小类 ID，用于按 riskDetailsId 追踪 L2 知识库变更。 */
    private Long riskDetailsId;

    /** 内容 hash；攻击特征事件会写入该值，便于索引侧幂等更新。 */
    private String contentHash;

    /** 数据版本；每次更新递增，帮助识别旧事件是否落后于最新数据。 */
    private Integer version;

    /** 事件载荷快照 JSON；删除场景依赖该快照删除索引中的旧文档。 */
    private String payload;

    /** ES 处理状态：0-待处理，1-成功，2-失败。 */
    private Integer esStatus;

    /** ES 处理状态说明，来自 KbSyncStatusEnums。 */
    private String esStatusDesc;

    /** Milvus 处理状态：0-待处理，1-成功，2-失败。 */
    private Integer milvusStatus;

    /** Milvus 处理状态说明，来自 KbSyncStatusEnums。 */
    private String milvusStatusDesc;

    /** 重试次数；当 ES 或 Milvus 任一回写失败时服务层会递增。 */
    private Integer retryCount;

    /** 下次重试时间；当前阶段预留，后续真实同步 worker 可使用。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime nextRetryTime;

    /** 最近一次失败原因；用于后台排查索引写入失败原因。 */
    private String lastError;

    /** 创建时间，即知识库变更事件产生时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /** 更新时间，即同步状态最近一次回写时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;
}
