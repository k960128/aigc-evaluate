package com.kant.llm.eval.report.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 评测报告指标计算工具。
 *
 * <p>集中处理 topN 清洗、比率和进度计算，避免不同报告字段出现四舍五入或空分母口径不一致。</p>
 */
public final class EvalReportMetricCalculator {

    /** 问题样本默认返回数量，避免报告响应体过大。 */
    public static final int DEFAULT_TOP_N = 10;

    /** 问题样本最大返回数量，用于保护接口响应大小和数据库查询成本。 */
    public static final int MAX_TOP_N = 50;

    private EvalReportMetricCalculator() {
    }

    /**
     * 清洗问题样本数量参数。
     *
     * <p>前端不传或传入非法值时使用默认值；传入过大值时截断到最大值，
     * 保证报告接口不会退化成全量样本导出接口。</p>
     */
    public static Integer sanitizeTopN(Integer topN) {
        if (topN == null || topN <= 0) {
            return DEFAULT_TOP_N;
        }
        return Math.min(topN, MAX_TOP_N);
    }

    /**
     * 计算 0 到 1 之间的比率，统一保留四位小数。
     *
     * <p>所有报告比率都通过该方法生成，空分母直接返回 0.0000，
     * 避免前端处理 NaN 或除零异常。</p>
     */
    public static BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0 || numerator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    /**
     * 计算 0 到 100 的整数进度百分比。
     *
     * <p>该方法沿用现有评测任务列表的进度口径：finishedCount / totalCount，
     * 与报告中的安全率、风险率等分离口径指标互不混用。</p>
     */
    public static Integer percent(Integer numerator, Integer denominator) {
        if (denominator == null || denominator <= 0 || numerator == null || numerator <= 0) {
            return 0;
        }
        return Math.min(numerator * 100 / denominator, 100);
    }
}
