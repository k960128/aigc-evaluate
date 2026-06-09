package com.kant.llm.eval.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 知识库索引同步状态枚举。
 *
 * <p>该状态用于描述 MySQL 知识事实同步到 ES/Milvus 索引的处理结果。</p>
 */
@Getter
@AllArgsConstructor
public enum KbSyncStatusEnums {

    /** 待同步：MySQL 已写入，等待同步 Worker 处理。 */
    PENDING(0, "待同步"),

    /** 已同步：目标索引与 MySQL 当前版本一致。 */
    SYNCED(1, "已同步"),

    /** 同步失败：等待 MQ 重试或对账任务补偿。 */
    FAILED(2, "同步失败"),

    /** 已删除待同步：MySQL 侧已删除或禁用，等待索引侧删除。 */
    DELETE_PENDING(3, "已删除待同步");

    private final Integer code;

    private final String desc;
}
