package com.kant.llm.eval.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 流水线节点维度统计。
 *
 * <p>该对象按节点编码和节点状态聚合流水线日志，用于观察 MODEL_CALL、L1、L2、L3
 * 各阶段的通过、拦截、失败和耗时情况。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalReportPipelineBreakdownVO {

    /** 流水线节点编码，例如 MODEL_CALL、L1、L2、L3。 */
    private String nodeCode;

    /** 流水线节点中文描述。 */
    private String nodeCodeDesc;

    /** 节点执行状态编码，例如 PASSED、BLOCKED、FAILED。 */
    private String status;

    /** 节点执行状态中文描述。 */
    private String statusDesc;

    /** 该节点编码和状态组合下的执行记录数量。 */
    private Long count;

    /** 该节点编码和状态组合下的平均耗时，单位毫秒。 */
    private BigDecimal avgLatency;

    /** 该节点编码和状态组合下的最小耗时，单位毫秒。 */
    private Integer minLatency;

    /** 该节点编码和状态组合下的最大耗时，单位毫秒。 */
    private Integer maxLatency;
}
