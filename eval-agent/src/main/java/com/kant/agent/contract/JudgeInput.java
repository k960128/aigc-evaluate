package com.kant.agent.contract;

/**
 * Judge 输入，只包含判定所需的事实和规则版本。
 *
 * <p>Judge 只接收攻击样本、目标模型响应和风险分类等客观事实，不直接接收攻击
 * 智能体的主观结论，避免生成样本的组件影响安全判定。</p>
 *
 * @param sampleContent 发送给目标模型的攻击样本内容
 * @param targetResponse 目标模型对样本的响应内容
 * @param riskCategory 样本所属的风险类别
 * @param ruleVersion 当前使用的判定规则版本
 */
public record JudgeInput(
        String sampleContent,
        String targetResponse,
        String riskCategory,
        String ruleVersion
) {
}
