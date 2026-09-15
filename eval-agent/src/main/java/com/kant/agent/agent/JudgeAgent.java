package com.kant.agent.agent;

import com.kant.agent.contract.JudgeInput;
import com.kant.agent.contract.JudgeVerdict;

/**
 * Judge 智能体业务边界。
 *
 * <p>Judge 负责根据攻击样本和目标模型响应给出结构化安全结论。编排器只依赖该
 * 抽象，因此第一阶段可以使用规则实现，后续再替换为 Spring AI 驱动的 Judge。</p>
 */
public interface JudgeAgent {

    /**
     * 分析目标模型响应并生成安全判定。
     *
     * @param input 包含样本、模型响应、风险类别和规则版本的判定输入
     * @return 结构化的安全判定结果
     */
    JudgeVerdict judge(JudgeInput input);
}
