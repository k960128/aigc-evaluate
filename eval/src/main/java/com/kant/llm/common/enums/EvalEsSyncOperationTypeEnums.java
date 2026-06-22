package com.kant.llm.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评测 ES 投影同步操作类型枚举。
 *
 * <p>第一阶段只执行 UPSERT，把 MySQL 最新行覆盖写入 ES；DELETE 先预留给后续物理删除或归档场景。</p>
 */
@Getter
@AllArgsConstructor
public enum EvalEsSyncOperationTypeEnums {

    /** 写入或更新 ES 文档，文档 ID 使用 MySQL 主键，天然具备幂等覆盖能力。 */
    UPSERT("UPSERT", "写入或更新"),

    /** 删除 ES 文档，当前版本预留，不主动触发。 */
    DELETE("DELETE", "删除");

    private final String code;

    private final String desc;
}
