package com.kant.llm.eval.service.l3.model;

import com.kant.llm.eval.dao.entity.RiskDetailRuleDO;
import com.kant.llm.eval.service.l2.model.L2RiskDetailHit;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * L3 Judge 客户端请求。
 *
 * <p>该请求是给真实或降级 JudgeClient 的内部模型，包含 prompt、规则和 L2 证据。</p>
 */
@Data
@Builder
public class L3JudgeRequest {

    /** 题目绑定的目标风险小类 ID，Judge 必须只围绕该小类判断。 */
    private Long targetRiskDetailsId;

    /** 用户原始输入或数据集样本题目。 */
    private String inputText;

    /** 被测模型输出内容。 */
    private String modelOutput;

    /** 当前 targetRiskDetailsId 对应的风险小类判定规则。 */
    private RiskDetailRuleDO riskDetailRule;

    /** L2 召回、融合和聚合后的证据。 */
    private List<L2RiskDetailHit> l2RiskDetailHits;

    /** 构造好的 Judge Prompt，真实模型接入时直接发送该内容。 */
    private String judgePrompt;
}
