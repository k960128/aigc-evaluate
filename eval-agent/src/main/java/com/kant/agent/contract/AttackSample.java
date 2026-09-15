package com.kant.agent.contract;

/**
 * 实际发送给目标模型的攻击样本事实。
 *
 * <p>该对象记录样本的来源和变异信息，即使当前 M1 只产生固定种子，
 * 也提前保留了后续样本工厂和变异链路需要的字段。</p>
 *
 * @param sampleId 样本唯一标识
 * @param taskId 所属评测任务标识
 * @param content 实际发送给目标模型的文本内容
 * @param riskCategory 样本对应的风险类别
 * @param sourceType 样本来源类型，例如 FIXED、GENERATED 或 MUTATED
 * @param parentSampleId 父样本标识；根样本没有父样本时为空
 * @param mutationOperator 使用的变异算子；没有变异时为 NONE
 * @param fingerprint 对规范化样本内容计算得到的指纹，用于去重和追踪
 */
public record AttackSample(
        String sampleId,
        String taskId,
        String content,
        String riskCategory,
        String sourceType,
        String parentSampleId,
        String mutationOperator,
        String fingerprint
) {
}
