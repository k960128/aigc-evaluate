package com.kant.agent.contract.assessment;

import com.kant.agent.contract.common.ContractChecks;

import java.time.Instant;
import java.util.List;

/**
 * 一次追加式安全判定记录。
 *
 * @param assessmentId  判定记录标识
 * @param scope         判定范围
 * @param subjectRef    被判定对象标识
 * @param verdict       判定结论
 * @param riskId        风险编码
 * @param ruleSetVersion 使用的规则集版本
 * @param judgeVersion 判定器实现或模型/提示词配置版本；历史未知必须明确标识
 * @param reason        可审计的简短判定依据，不保存模型内部思维链
 * @param evidenceRefs  支撑结论的证据引用
 * @param createdAt     判定时间
 */
public record Assessment(
        String assessmentId,
        AssessmentScope scope,
        String subjectRef,
        AssessmentVerdict verdict,
        String riskId,
        String ruleSetVersion,
        String judgeVersion,
        String reason,
        List<String> evidenceRefs,
        Instant createdAt) {

    /**
     * 校验判定结果并冻结证据引用。
     */
    public Assessment {
        assessmentId = ContractChecks.maxLength(
                ContractChecks.notBlank(assessmentId, "assessmentId"), 96, "assessmentId");
        scope = ContractChecks.required(scope, "scope");
        subjectRef = ContractChecks.maxLength(
                ContractChecks.notBlank(subjectRef, "subjectRef"), 96, "subjectRef");
        verdict = ContractChecks.required(verdict, "verdict");
        riskId = ContractChecks.maxLength(
                ContractChecks.notBlank(riskId, "riskId"), 128, "riskId");
        ruleSetVersion = ContractChecks.maxLength(
                ContractChecks.notBlank(ruleSetVersion, "ruleSetVersion"), 128, "ruleSetVersion");
        judgeVersion = ContractChecks.maxLength(
                ContractChecks.notBlank(judgeVersion, "judgeVersion"), 128, "judgeVersion");
        reason = ContractChecks.maxLength(
                ContractChecks.notBlank(reason, "reason"), 2000, "reason");
        evidenceRefs = List.copyOf(ContractChecks.notEmpty(evidenceRefs, "evidenceRefs"));
        createdAt = ContractChecks.required(createdAt, "createdAt");
    }
}
