package com.kant.agent.contract.assessment;

import com.kant.agent.contract.common.ContractChecks;
import com.kant.agent.contract.common.ExecutionContext;

import java.util.List;

/**
 * 请求判定服务分析一组既有事实的命令。
 *
 * @param context        执行业务上下文
 * @param scope          判定范围
 * @param subjectRef     被判定的探测、会话或场景标识
 * @param riskId         风险编码
 * @param ruleSetVersion 规则集版本
 * @param evidenceRefs   只读证据引用列表
 */
public record AssessmentCommand(
        ExecutionContext context,
        AssessmentScope scope,
        String subjectRef,
        String riskId,
        String ruleSetVersion,
        List<String> evidenceRefs) {

    /**
     * 校验判定命令并冻结证据引用。
     */
    public AssessmentCommand {
        context = ContractChecks.required(context, "context");
        scope = ContractChecks.required(scope, "scope");
        subjectRef = ContractChecks.maxLength(
                ContractChecks.notBlank(subjectRef, "subjectRef"), 96, "subjectRef");
        riskId = ContractChecks.maxLength(
                ContractChecks.notBlank(riskId, "riskId"), 128, "riskId");
        ruleSetVersion = ContractChecks.maxLength(
                ContractChecks.notBlank(ruleSetVersion, "ruleSetVersion"), 128, "ruleSetVersion");
        evidenceRefs = List.copyOf(ContractChecks.notEmpty(evidenceRefs, "evidenceRefs"));
    }
}
