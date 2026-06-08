package com.kant.llm.eval.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评测流水线节点状态枚举。
 *
 * <p>节点状态描述某一次节点执行历史的结果，不直接等同于样本最终状态或批次状态。</p>
 */
@Getter
@AllArgsConstructor
public enum PipelineNodeStatusEnums {

    /** 执行中：节点已经开始处理，但还没有产生最终结果。 */
    RUNNING("RUNNING", "执行中"),

    /** 通过：节点处理完成，并允许样本进入后续流程或完成当前阶段。 */
    PASSED("PASSED", "通过"),

    /** 拦截：节点命中风险或短路规则，样本不再进入后续安全判定节点。 */
    BLOCKED("BLOCKED", "拦截"),

    /** 失败：节点执行过程中发生异常。 */
    FAILED("FAILED", "失败"),

    /** 跳过：节点因为前置状态、幂等或业务条件未实际执行。 */
    SKIPPED("SKIPPED", "跳过"),

    /** 终止：用户主动停止批次后，该节点不再继续执行或写入成功结果。 */
    STOPPED("STOPPED", "终止");

    private final String code;

    private final String desc;

}
