package com.kant.llm.eval.service.l3.model;

import com.kant.llm.eval.common.enums.L2DecisionTypeEnums;
import com.kant.llm.eval.service.l2.model.L2RiskDetailHit;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * L3 判定请求。
 *
 * <p>该请求由执行链路在 L2 PASS_TO_L3 时构造，携带题目、模型输出、L1 warning、
 * L2 证据和 L2 节点日志快照，供 L3 Judge 在受控范围内裁决。</p>
 */
@Data
@Builder
public class L3EvaluationRequest {

    /** 评测任务 ID。 */
    private Long taskId;

    /** 任务执行批次 ID。 */
    private Long taskDetailId;

    /** 评测结果明细 ID。 */
    private Long resultDetailId;

    /** 数据集样本 ID。 */
    private Long sampleId;

    /** 题目绑定的目标风险小类 ID，L3 只能围绕该风险小类判断。 */
    private Long targetRiskDetailsId;

    /** 用户原始输入或数据集样本题目。 */
    private String inputText;

    /** 被测模型输出内容。 */
    private String modelOutput;

    /** L1 warning 命中的风险小类 ID 列表。 */
    private List<Long> l1WarningTags;

    /** L2 路由决策，正常情况下应为 PASS_TO_L3。 */
    private L2DecisionTypeEnums l2Decision;

    /** L2 路由原因。 */
    private String l2RouteReason;

    /** L2 按风险小类聚合后的证据列表。 */
    private List<L2RiskDetailHit> l2RiskDetailHits;

    /** L2 输出快照，来自 eval_pipeline_node_detail.output_snapshot。 */
    private Map<String, Object> l2OutputSnapshot;

    /** L2 节点结构化结果，来自 eval_pipeline_node_detail.node_result。 */
    private Map<String, Object> l2NodeResult;
}
