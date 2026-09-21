package com.kant.agent.contract.common;

/**
 * 评测活动的执行预算。
 *
 * <p>预算由用户配置与后端授权策略共同确定，智能体不能自行扩大预算。</p>
 *
 * <p>本对象只描述预算上限，不记录实际消耗。
 * 实际调用次数、Token 消耗和剩余预算由后续编排器统一管理。</p>
 *
 * <p>当前所有预算必须为正数，不使用 0 或 -1 表示无限制。</p>
 *
 * @param maxEvaluationSamples 本次活动最多允许执行的评测样本数
 * @param maxTargetCalls       本次活动最多允许调用目标模型的次数，重试也计入
 * @param maxAgentCalls        本次活动中各智能体累计允许的模型调用次数，
 *                            包含规划、修订和结构化输出修复等调用；
 *                            不包含目标模型调用和流水线 Judge 调用
 * @param maxAgentTokens       智能体模型调用累计允许消耗的 Token 数，
 *                            包含输入和输出，不包含目标模型及流水线 Judge 的消耗
 * @param maxPlanningAttempts  最多允许生成计划的次数，首次生成也计入
 */
public record ExecutionBudget(
        int maxEvaluationSamples,
        int maxTargetCalls,
        int maxAgentCalls,
        long maxAgentTokens,
        int maxPlanningAttempts
) {

    /**
     * 创建预算对象时校验数值，防止非法预算进入后续业务流程。
     */
    public ExecutionBudget {
        requirePositive(maxEvaluationSamples, "maxEvaluationSamples");
        requirePositive(maxTargetCalls, "maxTargetCalls");
        requirePositive(maxAgentCalls, "maxAgentCalls");
        requirePositive(maxAgentTokens, "maxAgentTokens");
        requirePositive(maxPlanningAttempts, "maxPlanningAttempts");
    }

    /**
     * 校验预算必须为正数。
     *
     * @param value     待校验的预算值
     * @param fieldName 字段名称，用于定位参数问题
     */
    private static void requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " 必须大于 0");
        }
    }
}