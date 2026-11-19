package com.kant.springai.agent.controller;

import com.kant.springai.agent.agent.SalesAgent;
import com.kant.springai.agent.tools.*;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/test/tool")
public class ToolTestController {
    private final SalesQueryTool salesQueryTool;
    private final SalesSummaryTool salesSummaryTool;
    private final SalesTrendTool salesTrendTool;
    private final ChartGeneratorTool chartGeneratorTool;
    private final AnomalyDetectionTool anomalyDetectionTool;
    private final SalesAgent salesAgent;

    public ToolTestController(SalesQueryTool salesQueryTool,
                              SalesSummaryTool salesSummaryTool,
                              SalesTrendTool salesTrendTool,
                              ChartGeneratorTool chartGeneratorTool,
                              AnomalyDetectionTool anomalyDetectionTool,
                              SalesAgent salesAgent) {
        this.salesQueryTool = salesQueryTool;
        this.salesSummaryTool = salesSummaryTool;
        this.salesTrendTool = salesTrendTool;
        this.chartGeneratorTool = chartGeneratorTool;
        this.anomalyDetectionTool = anomalyDetectionTool;
        this.salesAgent = salesAgent;
    }

    // -------- 工具一 --------
    record QueryRequest(String startDate, String endDate,
                        String regionName, String reqName, int limit) {
    }

    @PostMapping("/query-orders")
    public String queryOrders(@RequestBody QueryRequest req) {
        return salesQueryTool.queryOrders(
                req.startDate(), req.endDate(), req.regionName(), req.reqName(), req.limit());
    }

    // -------- 工具二 --------
    record RankRequest(String startDate, String endDate, String regionName, int topN) {
    }

    record RangeRequest(String startDate, String endDate) {
    }

    record ProductRankRequest(String startDate, String endDate, int topN) {
    }

    @PostMapping("/top-reps")
    public String topReps(@RequestBody RankRequest req) {
        return salesSummaryTool.getTopReps(
                req.startDate(), req.endDate(), req.regionName(), req.topN());
    }

    @PostMapping("/region-ranking")
    public String regionRanking(@RequestBody RangeRequest req) {
        return salesSummaryTool.getRegionRanking(req.startDate(), req.endDate());
    }

    @PostMapping("/top-products")
    public String topProducts(@RequestBody ProductRankRequest req) {
        return salesSummaryTool.getTopProducts(req.startDate(), req.endDate(), req.topN());
    }

    // -------- 工具三 --------
    record MomRequest(String currentStart, String currentEnd,
                      String prevStart, String prevEnd, String regionName) {
    }

    record YoyRequest(String startDate, String endDate, String regionName) {
    }

    record TrendRequest(int months, String regionName) {
    }

    @PostMapping("/month-over-month")
    public String monthOverMonth(@RequestBody MomRequest req) {
        return salesTrendTool.calcMonthOverMonth(
                req.currentStart(), req.currentEnd(),
                req.prevStart(), req.prevEnd(), req.regionName());
    }

    @PostMapping("/year-over-year")
    public String yearOverYear(@RequestBody YoyRequest req) {
        return salesTrendTool.calcYearOverYear(
                req.startDate(), req.endDate(), req.regionName());
    }

    @PostMapping("/monthly-trend")
    public String monthlyTrend(@RequestBody TrendRequest req) {
        return salesTrendTool.getMonthlyTrend(req.months(), req.regionName());
    }

    // -------- 工具四 --------
    record LineChartRequest(int months, String regionName, String title) {
    }

    record BarChartRequest(String dimension, String startDate, String endDate, String title) {
    }

    record PieChartRequest(String dimension, String startDate, String endDate, String title) {
    }

    @PostMapping("/line-chart")
    public String lineChart(@RequestBody LineChartRequest req) {
        return chartGeneratorTool.generateLineChart(req.months(), req.regionName(), req.title());
    }

    @PostMapping("/bar-chart")
    public String barChart(@RequestBody BarChartRequest req) {
        return chartGeneratorTool.generateBarChart(
                req.dimension(), req.startDate(), req.endDate(), req.title());
    }

    @PostMapping("/pie-chart")
    public String pieChart(@RequestBody PieChartRequest req) {
        return chartGeneratorTool.generatePieChart(
                req.dimension(), req.startDate(), req.endDate(), req.title());
    }

    // -------- 工具五 --------
    @PostMapping("/detect-anomalies")
    public String detectAnomalies() {
        return anomalyDetectionTool.detectAllAnomalies();
    }

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest req) {
        // chat() 的第三个参数 today 对应 System Prompt 里的{today} 变量
        String today = LocalDate.now().toString();
        return salesAgent.chat("test-session-001", req.message(), today);
    }

    public record ChatRequest(String message) {
    }
}
