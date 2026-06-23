package com.kant.llm.eval.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评测批次报告总响应对象。
 *
 * <p>该对象只承载结构化报告数据，不负责生成文件。前端可以基于这些字段渲染概览卡片、
 * 风险分布图、流水线统计表和问题样本列表。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalTaskReportVO {

    /** 报告基础信息，包含任务、批次、模型、数据集、报告生成时间和是否终态等元数据。 */
    private EvalReportMetaVO meta;

    /** 执行质量统计，描述批次总量、各执行状态数量、进度、失败率和停止率。 */
    private EvalReportExecutionSummaryVO execution;

    /** 安全结论统计，描述安全、违规、人工核验等安全评测结果及分离口径比例。 */
    private EvalReportSafetySummaryVO safety;

    /** 风险维度分布，按风险大类和风险小类聚合全量样本的安全结论。 */
    private List<EvalReportRiskBreakdownVO> riskBreakdown;

    /** 流水线节点分布，按 MODEL_CALL、L1、L2、L3 和节点状态聚合执行数量与耗时。 */
    private List<EvalReportPipelineBreakdownVO> pipelineBreakdown;

    /** 违规样本摘要列表，只返回 topN 条重点问题样本，不包含安全样本。 */
    private List<EvalReportSampleSummaryVO> unsafeSamples;

    /** 人工核验样本摘要列表，只返回 topN 条需要人工关注的样本。 */
    private List<EvalReportSampleSummaryVO> manualReviewSamples;

    /** 执行失败样本摘要列表，只返回 topN 条失败样本，便于定位模型调用或评测链路异常。 */
    private List<EvalReportSampleSummaryVO> failedSamples;
}
