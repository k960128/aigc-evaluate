package com.kant.llm.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评测 ES 投影同步聚合类型枚举。
 *
 * <p>该枚举只服务 eval_result_detail 与 eval_pipeline_node_detail 两类读模型投影，
 * 不复用 L2 知识库同步事件，避免结果日志投影和知识库索引同步职责混在一起。</p>
 */
@Getter
@AllArgsConstructor
public enum EvalEsSyncAggregateTypeEnums {

    /** 评测结果明细，对应 MySQL 表 eval_result_detail。 */
    RESULT_DETAIL("RESULT_DETAIL", "评测结果明细"),

    /** 流水线节点日志，对应 MySQL 表 eval_pipeline_node_detail。 */
    PIPELINE_NODE("PIPELINE_NODE", "流水线节点日志");

    private final String code;

    private final String desc;

    /**
     * 根据编码解析枚举，编码不匹配时返回 null，调用方决定降级或报错。
     */
    public static EvalEsSyncAggregateTypeEnums of(String code) {
        if (code == null) {
            return null;
        }
        for (EvalEsSyncAggregateTypeEnums item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
