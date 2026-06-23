package com.kant.llm.eval.report.dao.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 流水线节点聚合查询行模型。
 *
 * <p>该模型对应节点日志聚合 SQL 的一行结果，包含数量和耗时统计。</p>
 */
@Data
public class EvalReportPipelineStatRow {

    /** 流水线节点编码，例如 MODEL_CALL、L1、L2、L3。 */
    private String nodeCode;

    /** 节点执行状态，例如 PASSED、BLOCKED、FAILED。 */
    private String status;

    /** 当前节点编码和状态组合下的节点日志数量。 */
    private Long nodeCount;

    /** 当前节点编码和状态组合下的平均耗时，单位毫秒。 */
    private BigDecimal avgLatency;

    /** 当前节点编码和状态组合下的最小耗时，单位毫秒。 */
    private Integer minLatency;

    /** 当前节点编码和状态组合下的最大耗时，单位毫秒。 */
    private Integer maxLatency;
}
