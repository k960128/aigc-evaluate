package com.kant.llm.agent.ai.constant;

/**
 * 安全评测助手的标准意图枚举。
 *
 * <p>该枚举是后端可信的意图集合，用于承接大模型意图识别结果。
 * 大模型输出的 intent 字符串必须先转换为该枚举，再进入 Slot Validator 或后续 Skill Router。
 * 这样可以避免模型输出未知字符串时直接污染业务路由。</p>
 */
public enum SecurityEvalIntent {
    /**
     * 三层安全评测链路诊断。
     *
     * <p>用于解释 L1 关键词/规则、L2 向量召回、L3 裁判模型的整体判定过程。</p>
     */
    FULL_LINK_TRACE,

    /**
     * 规则、词库、特征、风险小类等评测资产溯源。
     *
     * <p>用于查询命中规则、特征来源、版本状态、同步状态、风险小类绑定关系等。</p>
     */
    RULE_FEATURE_TRACE,

    /**
     * 误杀、漏判或判定不一致原因分析。
     *
     * <p>用于定位问题可能出在词库、向量召回、白样本、阈值、L3 裁判规则或数据集绑定等环节。</p>
     */
    MISJUDGEMENT_ANALYSIS,

    /**
     * 安全评测知识问答。
     *
     * <p>用于回答 31 小类定义、风险边界、安全例外、规则依据、合规口径等问题。</p>
     */
    COMPLIANCE_QA,

    /**
     * 历史相似 Bad Case 或人工判例检索。
     *
     * <p>用于辅助人工复核、保持判罚一致性、查找相似历史处置记录。</p>
     */
    SIMILAR_CASE_RETRIEVAL,

    /**
     * 风险资产自然语言查询。
     *
     * <p>用于查询风险小类、词库、特征库、评测结果、同步状态、误杀统计等平台资产数据。</p>
     */
    ASSET_DATA_QUERY,

    /**
     * 风险特征提取。
     *
     * <p>仅用于从新风险、新漏洞或 Bad Case 中提取待审核特征，不承担样本生成或攻击变异职责。</p>
     */
    FEATURE_EXTRACTION,

    /**
     * 报告与整改建议辅助。
     *
     * <p>用于基于已有评测任务、批次或结果明细生成摘要、整改建议、复测建议。</p>
     */
    REPORT_ASSISTANCE,

    /**
     * 安全评测相关普通咨询。
     *
     * <p>用于处理不需要进入具体诊断、检索或查询工具的常规安全评测问答。</p>
     */
    GENERAL_CHAT,

    /**
     * 当前安全评测助手不处理的请求。
     *
     * <p>包括与安全评测无关，或虽然相关但超出当前助手边界的请求。</p>
     */
    OUT_OF_SCOPE,

    /**
     * 后端无法识别的异常意图。
     *
     * <p>该值不应由 Prompt 主动输出，只用于后端兜底模型异常、空值或拼写错误。</p>
     */
    UNKNOWN;

    /**
     * 将大模型输出的 intent 字符串转换为后端标准枚举。
     *
     * <p>模型输出不可被直接信任，因此这里对空值、空白字符串和未知枚举值做兜底，
     * 统一转换为 {@link #UNKNOWN}，后续由 Slot Validator 阻断执行链路。</p>
     *
     * @param value 大模型输出的 intent 字符串
     * @return 后端标准安全评测意图枚举；无法识别时返回 {@link #UNKNOWN}
     */
    public static SecurityEvalIntent from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return SecurityEvalIntent.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
