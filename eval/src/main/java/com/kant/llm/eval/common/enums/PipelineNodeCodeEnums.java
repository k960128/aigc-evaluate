package com.kant.llm.eval.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评测流水线节点编码枚举。
 *
 * <p>节点编码用于标识单条样本当前经过的处理阶段，后续 L2/L3 接入时继续复用该枚举。</p>
 */
@Getter
@AllArgsConstructor
public enum PipelineNodeCodeEnums {

    /** 被测模型调用节点，负责把样本 Prompt 发送给目标大模型并获取回复。 */
    MODEL_CALL("MODEL_CALL", "模型调用"),

    /** L1 字面量拦截节点，负责使用 AC 自动机做风险词极速判定。 */
    L1("L1", "L1字面量拦截"),

    /** L2 召回节点，预留给 ES + Milvus 双路召回。 */
    L2("L2", "L2双路召回"),

    /** L3 Judge 裁判层，只处理 L2 模糊区并输出最终裁决或人工核验。 */
    L3("L3", "L3 Judge 裁判层");

    private final String code;

    private final String desc;

}
