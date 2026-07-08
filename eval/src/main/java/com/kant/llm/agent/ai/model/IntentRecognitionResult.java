package com.kant.llm.agent.ai.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 意图识别结果
 */
public record IntentRecognitionResult(
        @JsonPropertyDescription("意图识别的简要推理理由。只描述为什么选择该意图，不要回答用户问题本身")
        String reasoning,

        @JsonPropertyDescription("用户问题是否与大模型安全评测平台相关。true 表示安全评测相关，false 表示与平台无关或超出当前助手能力边界")
        boolean related,

        @JsonPropertyDescription("""
                意图识别结果，只能选择一个：
                FULL_LINK_TRACE-三层安全评测链路诊断，解释 L1/L2/L3 判定过程；
                RULE_FEATURE_TRACE-规则、词库、特征、风险小类、版本、状态、同步情况溯源；
                MISJUDGEMENT_ANALYSIS-误杀或漏判原因分析；
                COMPLIANCE_QA-31 小类、风险定义、判定边界、安全例外、规则依据问答；
                SIMILAR_CASE_RETRIEVAL-历史相似 Bad Case 或人工判例检索；
                ASSET_DATA_QUERY-风险资产、词库、特征库、评测结果、同步状态、误杀统计等自然语言查询；
                FEATURE_EXTRACTION-从新风险、新漏洞或 Bad Case 中提取风险特征并形成待审核信息；
                REPORT_ASSISTANCE-基于已有评测结果生成报告摘要、整改建议、复测建议；
                GENERAL_CHAT-安全评测相关普通咨询；
                OUT_OF_SCOPE-与安全评测助手无关或当前不应处理的请求
                """)
        String intent,

        @JsonPropertyDescription("意图识别置信度，取值范围 0 到 1")
        Double confidence,

        @JsonPropertyDescription("是否建议人工复核。涉及规则变更、特征入库、阈值调整、灰色边界定性、报告发布等场景时应为 true")
        boolean requireHumanReview,

        @JsonPropertyDescription("当前用户输入是否缺少执行该意图所需的关键槽位，需要继续向用户澄清")
        boolean needClarification,

        @JsonPropertyDescription("缺失的关键槽位名称列表。字段名必须来自 entities 中的字段，例如 result_detail_id、input_text、risk_details_id")
        List<String> missingSlots,

        @JsonPropertyDescription("当 needClarification 为 true 时，给用户的一句话澄清问题；否则为空")
        String clarificationQuestion,

        @JsonPropertyDescription("从用户输入中抽取的安全评测相关实体和参数")
        IntentRecognitionResult.Entities entities) {

    /**
     * 安全评测实体与参数槽位。
     */
    public record Entities(
            @JsonPropertyDescription("待诊断、待分析或待问答的用户输入文本、Prompt、原始问题")
            String input_text,

            @JsonPropertyDescription("待诊断或待分析的大模型输出、Response、Output")
            String model_output,

            @JsonPropertyDescription("用户显式提到的风险小类 ID，例如 risk_details_id=30")
            Long risk_details_id,

            @JsonPropertyDescription("用户显式提到的风险小类名称，例如提示词注入、隐私泄露、恶意代码生成")
            String risk_details_name,

            @JsonPropertyDescription("用户显式提到的风险大类 ID")
            Long category_id,

            @JsonPropertyDescription("用户显式提到的风险大类名称")
            String category_name,

            @JsonPropertyDescription("用户提到的业务场景，例如客服、教育、医疗、金融、内容创作")
            String scenario,

            @JsonPropertyDescription("用户提到的模型名称或被测模型标识")
            String model_name,

            @JsonPropertyDescription("用户提到的评测任务 ID")
            Long task_id,

            @JsonPropertyDescription("用户提到的评测执行批次 ID")
            Long task_detail_id,

            @JsonPropertyDescription("用户提到的单条评测结果明细 ID")
            Long result_detail_id,

            @JsonPropertyDescription("用户提到的数据集 ID")
            Long dataset_id,

            @JsonPropertyDescription("用户提到的样本 ID。这里只用于定位已有评测样本，不用于样本生成")
            Long sample_id,

            @JsonPropertyDescription("用户提到的规则 ID")
            Long rule_id,

            @JsonPropertyDescription("用户提到的特征 ID、攻击特征 ID、知识库特征 ID")
            Long feature_id,

            @JsonPropertyDescription("用户提到的词库关键词 ID 或敏感词 ID")
            Long keyword_id,

            @JsonPropertyDescription("用户提到的查询时间范围，例如最近7天、过去一个月、2026-06-01 到 2026-06-30")
            String time_range,

            @JsonPropertyDescription("用户希望返回的结果数量，例如 Top 3、前10条")
            Integer top_k,

            @JsonPropertyDescription("用户显式指定的 L2 向量召回阈值，仅用于本次沙箱诊断，不代表线上全局配置")
            Double l2_threshold,

            @JsonPropertyDescription("用户显式指定的 L2 向量召回 TopK，仅用于本次沙箱诊断，不代表线上全局配置")
            Integer l2_top_k,

            @JsonPropertyDescription("用户希望限定诊断或查询的风险小类 ID 范围")
            List<Long> risk_scope_ids,

            @JsonPropertyDescription("用户显式指定的 L3 裁判 Prompt 版本或裁判模型版本，仅用于本次诊断")
            String judge_version,

            @JsonPropertyDescription("用户要求的输出格式，例如诊断报告、JSON 卡片、Markdown 表格、摘要")
            String output_format,

            @JsonPropertyDescription("用户请求中出现的疑似新风险特征、判定线索、关键词或待审核特征片段")
            List<String> candidate_features,

            @JsonPropertyDescription("意图识别时需要传递给后续查询、检索或诊断工具的其他关键词")
            List<String> keywords) {
    }
}
