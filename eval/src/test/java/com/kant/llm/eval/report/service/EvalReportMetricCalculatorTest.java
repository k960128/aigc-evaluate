package com.kant.llm.eval.report.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvalReportMetricCalculator 单元测试。
 *
 * <p>这些测试保证报告比率、空分母和 topN 限制口径稳定。</p>
 */
class EvalReportMetricCalculatorTest {

    @Test
    void rateHandlesEmptyDenominator() {
        // 空分母统一返回 0.0000，前端不需要额外处理 NaN 或异常值。
        assertThat(EvalReportMetricCalculator.rate(1, 0)).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    @Test
    void rateRoundsToFourDecimals() {
        // 比率统一保留四位小数，确保报告字段格式稳定。
        assertThat(EvalReportMetricCalculator.rate(2, 3)).isEqualByComparingTo(new BigDecimal("0.6667"));
    }

    @Test
    void sanitizeTopNCapsLargeValues() {
        // topN 既有默认值也有最大值，避免报告接口被用成全量样本导出。
        assertThat(EvalReportMetricCalculator.sanitizeTopN(999)).isEqualTo(50);
        assertThat(EvalReportMetricCalculator.sanitizeTopN(null)).isEqualTo(10);
    }
}
