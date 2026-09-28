package com.kant.springai.agent.agent.sales;

import com.kant.springai.agent.common.config.SalesMultiAgentModelProvider;
import com.kant.springai.agent.tools.ChartGeneratorTool;
import org.springframework.stereotype.Component;

/**
 * 负责生成 ECharts 数据的专业 Agent。
 */
@Component
public class SalesChartAgent extends AbstractSalesWorkerAgent {

    public static final String AGENT_NAME = "chart";

    private static final String SYSTEM_PROMPT = """
            你是销售图表生成 Agent，只负责根据用户要求生成 ECharts 图表数据。

            你的工具边界：
            - 生成销售趋势折线图；
            - 生成销售对比柱状图；
            - 生成销售占比饼图。

            工作要求：
            - 必须调用图表工具生成数据，不能手写或猜测 JSON；
            - 根据任务选择最合适的图表类型；
            - 工具结果以 CHART_JSON: 开头时，必须完整、原样保留该行；
            - 不使用 Markdown 代码块包裹 CHART_JSON；
            - 不负责额外的数据分析和管理建议。
            """;

    public SalesChartAgent(SalesMultiAgentModelProvider modelProvider,
                           ChartGeneratorTool chartGeneratorTool) {
        super(modelProvider, chartGeneratorTool);
    }

    @Override
    public String name() {
        return AGENT_NAME;
    }

    @Override
    public String description() {
        return "按需生成折线图、柱状图或饼图的 ECharts JSON；仅在用户要求可视化时调用。";
    }

    @Override
    protected String systemPrompt() {
        return SYSTEM_PROMPT;
    }
}
