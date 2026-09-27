package com.kant.springai.agent.tools;

import com.kant.springai.agent.dto.MonthlyTrendDTO;
import com.kant.springai.agent.service.SalesQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Component
public class SalesTrendTool {

    private final SalesQueryService salesQueryService;

    public SalesTrendTool(SalesQueryService salesQueryService) {
        this.salesQueryService = salesQueryService;
    }

    /**
     * 环比分析（当期 vs 上一期）
     */
    @Tool(name = "calcMonthOverMonth", description = "计算销售环比增长率（当期与上一期对比）。适用于：本月比上月、本季比上季、" +
            "环比增长/下降多少、最近两期对比等场景。")
    public String calcMonthOverMonth(
            @ToolParam(description = "当前周期开始日期，格式 yyyy-MM-dd") String currentStart,
            @ToolParam(description = "当前周期结束日期，格式 yyyy-MM-dd") String currentEnd,
            @ToolParam(description = "对比周期开始日期，格式 yyyy-MM-dd。传 null 则自动计算上一个等长周期") String prevStart,
            @ToolParam(description = "对比周期结束日期，格式 yyyy-MM-dd。传 null 则自动计算上一个等长周期") String prevEnd,
            @ToolParam(description = "大区名称，如：华东区。传 null 表示全公司") String regionName) {

        log.info("工具调用-calcMonthOverMonth: current={}/{}, prev={}/{}, region={}",
                currentStart, currentEnd, prevStart, prevEnd, regionName);

        try {
            LocalDate cStart = LocalDate.parse(currentStart);
            LocalDate cEnd = LocalDate.parse(currentEnd);

            // 自动计算上一个等长周期
            LocalDate pStart, pEnd;
            if (prevStart == null || prevStart.isBlank()) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(cStart, cEnd) + 1;
                pEnd = cStart.minusDays(1);
                pStart = pEnd.minusDays(days - 1);
            } else {
                pStart = LocalDate.parse(prevStart);
                pEnd = LocalDate.parse(prevEnd);
            }

            Long regionId = resolveRegionId(regionName);
            if (regionId == null && regionName != null && !regionName.isBlank()) {
                return "未找到大区：" + regionName;
            }

            BigDecimal currentAmount = salesQueryService.queryTotalAmount(regionId, cStart, cEnd);
            BigDecimal prevAmount = salesQueryService.queryTotalAmount(regionId, pStart, pEnd);
            BigDecimal growthRate = salesQueryService.calcGrowthRate(currentAmount, prevAmount);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("环比分析（%s%s）：\n\n",
                    regionName != null && !regionName.isBlank() ? regionName + "，" : "全公司，",
                    ""));
            sb.append(String.format("当前周期（%s 至 %s）：¥%,.0f\n", cStart, cEnd, currentAmount));
            sb.append(String.format("对比周期（%s 至 %s）：¥%,.0f\n", pStart, pEnd, prevAmount));

            if (growthRate == null) {
                sb.append("对比周期无数据，无法计算增长率");
            } else {
                String trend = growthRate.compareTo(BigDecimal.ZERO) >= 0 ? "↑ 增长" : "↓ 下降";
                sb.append(String.format("环比变化：%s %.1f%%（%s ¥%,.0f）",
                        trend,
                        growthRate.abs(),
                        growthRate.compareTo(BigDecimal.ZERO) >= 0 ? "增加" : "减少",
                        currentAmount.subtract(prevAmount).abs()));
            }
            return sb.toString();

        } catch (DateTimeParseException e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式";
        } catch (Exception e) {
            log.error("计算环比失败", e);
            return "计算环比数据时出现问题，请稍后重试";
        }
    }

    /**
     * 同比分析（今年 vs 去年同期）
     */
    @Tool(name = "calcYearOverYear", description = "计算销售同比增长率（与去年同期对比）。适用于：今年和去年同期比、" +
            "同比增长率、年度对比、YoY 等场景。")
    public String calcYearOverYear(
            @ToolParam(description = "查询开始日期，格式 yyyy-MM-dd（今年的日期）") String startDate,
            @ToolParam(description = "查询结束日期，格式 yyyy-MM-dd（今年的日期）") String endDate,
            @ToolParam(description = "大区名称，如：华东区。传 null 表示全公司") String regionName) {

        log.info("工具调用-calcYearOverYear: start={}, end={}, region={}", startDate, endDate, regionName);

        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            // 去年同期
            LocalDate prevStart = start.minusYears(1);
            LocalDate prevEnd = end.minusYears(1);

            Long regionId = resolveRegionId(regionName);
            if (regionId == null && regionName != null && !regionName.isBlank()) {
                return "未找到大区：" + regionName;
            }

            BigDecimal thisYear = salesQueryService.queryTotalAmount(regionId, start, end);
            BigDecimal lastYear = salesQueryService.queryTotalAmount(regionId, prevStart, prevEnd);
            BigDecimal growthRate = salesQueryService.calcGrowthRate(thisYear, lastYear);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("同比分析（%s）：\n\n",
                    regionName != null && !regionName.isBlank() ? regionName : "全公司"));
            sb.append(String.format("今年（%s 至 %s）：¥%,.0f\n", start, end, thisYear));
            sb.append(String.format("去年（%s 至 %s）：¥%,.0f\n", prevStart, prevEnd, lastYear));

            if (growthRate == null) {
                sb.append("去年同期无数据，无法计算同比增长率");
            } else {
                String trend = growthRate.compareTo(BigDecimal.ZERO) >= 0 ? "↑ 同比增长" : "↓ 同比下降";
                sb.append(String.format("同比变化：%s %.1f%%", trend, growthRate.abs()));
            }
            return sb.toString();

        } catch (DateTimeParseException e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式";
        } catch (Exception e) {
            log.error("计算同比失败", e);
            return "计算同比数据时出现问题，请稍后重试";
        }
    }

    /**
     * 月度趋势序列
     */
    @Tool(name = "getMonthlyTrend", description = "获取近 N 个月的月度销售趋势数据。适用于：近几个月的趋势、月度变化情况、" +
            "销售走势、趋势是上升还是下降等场景。如果用户要画折线图，先调用此工具获取数据。")
    public String getMonthlyTrend(
            @ToolParam(description = "查看近多少个月，如 6 表示近 6 个月，最大 24") int months,
            @ToolParam(description = "大区名称，如：华东区。传 null 表示全公司") String regionName) {

        log.info("工具调用-getMonthlyTrend: months={}, region={}", months, regionName);

        try {
            int m = Math.min(Math.max(months, 1), 24);
            Long regionId = resolveRegionId(regionName);

            List<MonthlyTrendDTO> trend = salesQueryService.queryMonthlyTrend(regionId, m);
            if (trend.isEmpty()) {
                return "暂无趋势数据";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("月度销售趋势（近 %d 个月%s）：\n\n",
                    m, regionName != null && !regionName.isBlank() ? "，" + regionName : "，全公司"));

            for (int i = 0; i < trend.size(); i++) {
                MonthlyTrendDTO dto = trend.get(i);
                String changeStr = "";
                if (i > 0) {
                    BigDecimal prev = trend.get(i - 1).totalAmount();
                    BigDecimal rate = salesQueryService.calcGrowthRate(dto.totalAmount(), prev);
                    if (rate != null) {
                        changeStr = rate.compareTo(BigDecimal.ZERO) >= 0
                                ? String.format(" (↑%.1f%%)", rate)
                                : String.format(" (↓%.1f%%)", rate.abs());
                    }
                }
                sb.append(String.format("%s：¥%,.0f  订单数：%d%s\n",
                        dto.month(), dto.totalAmount(), dto.orderCount(), changeStr));
            }

            // 计算整体趋势方向
            if (trend.size() >= 2) {
                BigDecimal first = trend.get(0).totalAmount();
                BigDecimal last = trend.get(trend.size() - 1).totalAmount();
                BigDecimal overallRate = salesQueryService.calcGrowthRate(last, first);
                if (overallRate != null) {
                    sb.append(String.format("\n整体趋势：%s %.1f%%",
                            overallRate.compareTo(BigDecimal.ZERO) >= 0 ? "上升" : "下降",
                            overallRate.abs()));
                }
            }
            return sb.toString();

        } catch (Exception e) {
            log.error("获取月度趋势失败", e);
            return "获取趋势数据时出现问题，请稍后重试";
        }
    }

    private Long resolveRegionId(String regionName) {
        if (regionName == null || regionName.isBlank()) return null;
        return salesQueryService.getRegionIdByName(regionName);
    }
}
