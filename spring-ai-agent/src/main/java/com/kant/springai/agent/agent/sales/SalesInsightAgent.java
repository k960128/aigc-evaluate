package com.kant.springai.agent.agent.sales;

import com.kant.springai.agent.common.config.SalesMultiAgentModelProvider;
import com.kant.springai.agent.tools.AnomalyDetectionTool;
import com.kant.springai.agent.tools.SalesTrendTool;
import org.springframework.stereotype.Component;

/**
 * 负责趋势与异常分析的专业 Agent。
 */
@Component
public class SalesInsightAgent extends AbstractSalesWorkerAgent {

    public static final String AGENT_NAME = "trend_risk";

    private static final String SYSTEM_PROMPT = """
            你是销售趋势与异常分析 Agent，只负责趋势计算和异常识别。

            你的工具边界：
            - 计算销售同比、环比；
            - 获取月度销售趋势；
            - 检测销售数据异常。

            工作要求：
            - 需要具体趋势或异常数据时必须调用工具；
            - 区分事实、计算结果和分析判断；
            - 说明比较周期及增长/下降幅度；
            - 不负责订单明细查询、排行榜和图表生成；
            - 不预测未来销售，不编造异常原因。
            """;

    public SalesInsightAgent(SalesMultiAgentModelProvider modelProvider,
                             SalesTrendTool salesTrendTool,
                             AnomalyDetectionTool anomalyDetectionTool) {
        super(modelProvider, salesTrendTool, anomalyDetectionTool);
    }

    @Override
    public String name() {
        return AGENT_NAME;
    }

    @Override
    public String description() {
        return "计算同比、环比和月度趋势，检测销售异常并给出基于数据的分析。";
    }

    @Override
    protected String systemPrompt() {
        return SYSTEM_PROMPT;
    }
}
