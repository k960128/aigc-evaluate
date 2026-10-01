package com.kant.agent.contract.validation;

import com.kant.agent.contract.common.ContractChecks;
import com.kant.agent.contract.budget.BudgetPolicy;
import java.util.List;

/**
 * 独立复现/固定回归的不可变执行配置。
 * @param kind REPLAY独立复现；REGRESSION标准评测的修复回归用途
 * @param activityId 关联原评测活动
 * @param taskId 本次独立验证任务引用，固定保存在计划中
 * @param findingId 当前归并发现
 * @param targetSnapshotRef 本次目标固定快照
 * @param authorizationRef 本次单独批准的验证授权引用
 * @param budget 本次独立预算，不偷用已结束探索的额度
 * @param cases 固定用例版本及完整步骤
 */
public record ValidationPlan(Kind kind,String activityId,String taskId,String findingId,String targetSnapshotRef,String authorizationRef,
        BudgetPolicy budget,List<CaseBinding> cases) {
    public enum Kind { REPLAY, REGRESSION }
    public ValidationPlan {
        ContractChecks.required(kind,"kind"); ContractChecks.notBlank(activityId,"activityId");
        ContractChecks.notBlank(taskId,"taskId"); ContractChecks.notBlank(findingId,"findingId");
        ContractChecks.notBlank(targetSnapshotRef,"targetSnapshotRef"); ContractChecks.notBlank(authorizationRef,"authorizationRef");
        ContractChecks.required(budget,"budget"); cases=List.copyOf(ContractChecks.notEmpty(cases,"cases"));
        ContractChecks.require(cases.size()<=20,"单次最多20个固定用例");
        for(var binding:cases) ContractChecks.require(findingId.equals(binding.definition().sourceFindingId()),"固定用例必须属于本发现");
        ContractChecks.require(cases.stream().map(c -> c.caseId()+":"+c.version()).distinct().count()==cases.size(),"同一固定版本不能重复执行冒充完整范围");
        ContractChecks.require(budget.maxCost()==null && budget.maxTokens()>0 && budget.maxConcurrentSessions()==1
                && budget.maxRetriesPerOperation()==0,"当前只支持正Token预算、串行且不自动重试，暂不支持费用硬上限");
        long turns=cases.stream().mapToLong(c -> c.definition().steps().size()).sum();
        ContractChecks.require(budget.maxTargetCalls()>=turns && budget.maxAttempts()>=cases.size(),"预算不足以执行完整固定用例");
        ContractChecks.require(cases.stream().allMatch(c -> c.definition().steps().size()<=budget.maxTurnsPerSession()),"会话轮数预算不足");
    }
    /** caseId/version是本次固定版本；复现尚无正式用例时使用独立来源绑定。 */
    public record CaseBinding(String caseId,long version,FixedCaseDefinition definition) {
        public CaseBinding { ContractChecks.notBlank(caseId,"caseId"); ContractChecks.positive(version,"version"); ContractChecks.required(definition,"definition"); }
    }
}
