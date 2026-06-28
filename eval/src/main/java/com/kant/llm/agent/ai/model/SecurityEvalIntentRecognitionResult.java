package com.kant.llm.agent.ai.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 安全评测助手意图识别结果。
 *
 * <p>用于把内部红队助手中的自然语言请求路由到对应能力：
 * 三层漏斗诊断、知识库问答、历史判例检索、样本变异、资产查询或特征提取等。</p>
 */
public record SecurityEvalIntentRecognitionResult(
        @JsonPropertyDescription("意图识别的推断理由")
        String reasoning,

        @JsonPropertyDescription("用户问题是否与大模型安全评测平台相关：true-安全评测相关，false-无关闲聊或平台能力边界外请求")
        boolean related,

        @JsonPropertyDescription("""
                意图识别结果，只能选择一个：
                FULL_LINK_TRACE-三层漏斗链路诊断；
                COMPLIANCE_QA-31小类/安全规范/判定边界问答；
                SIMILAR_CASE_RETRIEVAL-历史相似Bad Case/判例检索；
                SAMPLE_MUTATION-红队攻击样本变异生成；
                ASSET_DATA_QUERY-风险资产/特征词/评测结果数据查询；
                FEATURE_EXTRACTION-新漏洞/越狱/风险特征提取入库建议；
                REPORT_ASSISTANCE-评测报告、风险摘要或整改建议生成；
                GENERAL_CHAT-安全评测相关普通咨询；
                OUT_OF_SCOPE-与平台无关或不应处理的请求
                """)
        String intent,

        @JsonPropertyDescription("意图识别置信度，取值范围0到1")
        Double confidence,

        @JsonPropertyDescription("是否建议人工复核；涉及规则启停、特征入库、线上策略变更、灰色边界定性时应为true")
        boolean requireHumanReview,

        @JsonPropertyDescription("从用户输入中提取的安全评测关键实体信息")
        Entities entities) {

    /**
     * 安全评测实体信息。
     */
    public record Entities(
            @JsonPropertyDescription("待诊断或待评测的用户输入/Prompt/User Input")
            String input_text,

            @JsonPropertyDescription("待诊断或待评测的大模型输出/Response/Output")
            String model_output,

            @JsonPropertyDescription("用户显式提到的风险小类ID，如risk_details_id=30")
            Long risk_details_id,

            @JsonPropertyDescription("用户显式提到的风险小类名称，如提示词注入攻击、恶意代码生成、PII泄露")
            String risk_details_name,

            @JsonPropertyDescription("用户显式提到的风险大类ID")
            Long category_id,

            @JsonPropertyDescription("用户显式提到的风险大类名称")
            String category_name,

            @JsonPropertyDescription("用户提到的业务场景或场景画像，如AI音乐创作、电信客服、未成年人陪伴教育")
            String scenario,

            @JsonPropertyDescription("用户提到的模型名称或被测模型标识")
            String model_name,

            @JsonPropertyDescription("用户提到的评测任务ID")
            Long task_id,

            @JsonPropertyDescription("用户提到的评测执行批次ID")
            Long task_detail_id,

            @JsonPropertyDescription("用户提到的单条评测结果明细ID")
            Long result_detail_id,

            @JsonPropertyDescription("用户提到的数据集ID")
            Long dataset_id,

            @JsonPropertyDescription("用户提到的样本ID")
            Long sample_id,

            @JsonPropertyDescription("用户提到的规则、特征词、攻击特征或知识库特征ID")
            Long feature_id,

            @JsonPropertyDescription("用户希望检索或生成的样本类型，如越狱、提示注入、隐私泄露、恶意代码、歧视仇恨")
            String sample_type,

            @JsonPropertyDescription("用户希望使用的样本变异策略，如角色扮演、隐喻改写、多轮诱导、编码混淆、业务伪装")
            List<String> mutation_strategies,

            @JsonPropertyDescription("用户提到的查询时间范围，如最近7天、过去6个月、2026-06-01到2026-06-30")
            String time_range,

            @JsonPropertyDescription("用户希望返回的结果数量，如Top 3、前10条")
            Integer top_k,

            @JsonPropertyDescription("用户显式指定的L2向量/召回阈值，仅用于沙箱诊断，不代表线上全局配置")
            Double l2_threshold,

            @JsonPropertyDescription("用户显式指定的L3裁判Prompt版本或裁判模型版本，仅用于沙箱诊断")
            String judge_version,

            @JsonPropertyDescription("用户要求的输出格式，如诊断报告、JSON卡片、Markdown表格、摘要")
            String output_format,

            @JsonPropertyDescription("用户请求中出现的疑似新风险特征、越狱片段、关键词或payload")
            List<String> candidate_features,

            @JsonPropertyDescription("意图识别时需要传递给后续工具的其他关键词")
            List<String> keywords) {
    }
}
