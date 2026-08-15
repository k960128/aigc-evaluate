package com.kant.llm.agent.ai.model;

import com.kant.llm.agent.ai.constant.SecurityEvalIntent;

import java.util.List;

/**
 * 安全评测意图槽位校验结果。
 *
 * <p>该对象是 {@code SecurityEvalSlotValidator} 的输出，用于告诉应用层：
 * 当前意图是否可以继续执行、是否需要向用户追问补充信息，以及具体缺失哪些槽位。</p>
 *
 * <p>注意：该结果是后端确定性校验后的结果，不直接信任大模型在
 * {@code SecurityEvalIntentRecognitionResult} 中返回的 needClarification 或 missingSlots。</p>
 *
 * @param executable            当前请求是否具备进入后续执行链路的最低条件
 * @param intent                后端归一化后的安全评测意图
 * @param needClarification     是否需要向用户发起澄清追问
 * @param missingSlots          缺失的关键槽位名称列表
 * @param clarificationQuestion 面向用户的一句话澄清问题；不需要澄清时为 null
 */
public record SecurityEvalSlotValidationResult(
        boolean executable,
        SecurityEvalIntent intent,
        boolean needClarification,
        List<String> missingSlots,
        String clarificationQuestion
) {
    /**
     * 构造“不可执行”的校验结果。
     *
     * <p>适用于无关请求、越界请求、未知意图等场景。该状态不要求用户补槽，
     * 应用层通常直接返回边界说明或兜底提示。</p>
     *
     * @param intent 后端识别到的意图；可能是 OUT_OF_SCOPE 或 UNKNOWN
     * @return 不可执行且不需要澄清的校验结果
     */
    public static SecurityEvalSlotValidationResult notExecutable(SecurityEvalIntent intent) {
        return new SecurityEvalSlotValidationResult(false, intent, false, List.of(), null);
    }

    /**
     * 构造“校验通过”的结果。
     *
     * <p>表示该意图所需的最低槽位已经满足，可以进入后续安全评测能力执行链路。
     * 当前阶段后续链路暂未接入 Skill Router，因此应用层只返回识别与校验结果。</p>
     *
     * @param intent 后端归一化后的安全评测意图
     * @return 可执行且不需要澄清的校验结果
     */
    public static SecurityEvalSlotValidationResult pass(SecurityEvalIntent intent) {
        return new SecurityEvalSlotValidationResult(true, intent, false, List.of(), null);
    }

    /**
     * 构造“需要澄清”的校验结果。
     *
     * <p>适用于意图已经明确，但缺少进入该意图执行链路所必需的定位信息。
     * 例如链路诊断缺少 result_detail_id 或待诊断文本。</p>
     *
     * @param intent                后端归一化后的安全评测意图
     * @param missingSlots          缺失的关键槽位名称
     * @param clarificationQuestion 面向用户的一句话澄清问题
     * @return 不可执行但需要澄清的校验结果
     */
    public static SecurityEvalSlotValidationResult clarify(
            SecurityEvalIntent intent,
            List<String> missingSlots,
            String clarificationQuestion
    ) {
        return new SecurityEvalSlotValidationResult(false, intent, true, missingSlots, clarificationQuestion);
    }
}
