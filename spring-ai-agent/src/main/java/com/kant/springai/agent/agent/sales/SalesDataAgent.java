package com.kant.springai.agent.agent.sales;

import com.kant.springai.agent.common.config.SalesMultiAgentModelProvider;
import com.kant.springai.agent.tools.SalesQueryTool;
import com.kant.springai.agent.tools.SalesSummaryTool;
import org.springframework.stereotype.Component;

/**
 * 负责销售明细、汇总与排名查询的专业 Agent。
 */
@Component
public class SalesDataAgent extends AbstractSalesWorkerAgent {

    public static final String AGENT_NAME = "sales_data";

    private static final String SYSTEM_PROMPT = """
            你是销售数据查询 Agent，只负责查询事实数据、汇总指标和排行榜。

            你的工具边界：
            - 查询销售订单明细；
            - 查询销售额、订单量等汇总指标；
            - 查询销售员、大区、产品排名。

            工作要求：
            - 涉及具体数字时必须调用工具；
            - 清楚标注查询时间范围和筛选条件；
            - 金额使用 ¥X,XXX 格式；
            - 不负责同比环比、异常判断和图表生成；
            - 工具没有返回的数据不得猜测。
            """;

    public SalesDataAgent(SalesMultiAgentModelProvider modelProvider,
                          SalesQueryTool salesQueryTool,
                          SalesSummaryTool salesSummaryTool) {
        super(modelProvider, salesQueryTool, salesSummaryTool);
    }

    @Override
    public String name() {
        return AGENT_NAME;
    }

    @Override
    public String description() {
        return "查询订单明细、销售汇总、销售员/大区/产品排名；负责提供可信的基础数据。";
    }

    @Override
    protected String systemPrompt() {
        return SYSTEM_PROMPT;
    }
}
