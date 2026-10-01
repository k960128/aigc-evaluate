package com.kant.llm.eval.agent.validation.dto;
import com.kant.agent.contract.budget.BudgetSnapshot;
import com.kant.agent.contract.validation.*;
import java.time.Instant;
import java.util.List;
/** 仅返回安全索引，绝不返回在途ProbeCommand内的受限原文。 */
public final class ValidationResponses {
    private ValidationResponses() {}
    /** candidateId候选；findingId根发现；status审核状态；version候选版本；definition固定配置；existingCaseId/baseCaseVersion为修订依据；createdAt首次创建。 */
    public record Candidate(String candidateId,String findingId,String status,long version,FixedCaseDefinition definition,String existingCaseId,Long baseCaseVersion,Instant createdAt) {}
    /** reviewId审核；decision审核动作；acceptedVersion审核后候选版本；caseId/caseVersion仅通过时生成；reason/actorRef/createdAt历史依据与人。 */
    public record CaseReview(String reviewId,String candidateId,String decision,long acceptedVersion,String caseId,Long caseVersion,
            String reason,String actorRef,Instant createdAt) {}
    /** 正式固定用例，approvalRef为审核引用；版本不可变，未经审核不能查询成正式资产。 */
    public record FormalCase(String caseId,long version,String approvalRef,FixedCaseDefinition definition,Instant createdAt) {}
    /**
     * @param runId 本次独立验证
     * @param taskType REGRESSION为STANDARD_EVALUATION，REPLAY为独立验证用途而非新探索
     * @param status 执行状态，不代表风险确认或修复
     * @param version 下一次停止命令expectedVersion
     * @param plan 冻结的目标、正式用例版本、原规则与独立预算
     * @param budget 已结算及预留额度，UNKNOWN不自动释放
     * @param results 新执行和新判定，不能替代原判定
     * @param stopRequested 停止意图
     * @param errorCode 停止/错误原因
     * @param errorSummary 安全摘要
     * @param pendingExecutionId 未确认/迟到操作的物理执行引用，可走受控探测查询
     * @param createdAt 首次受理时间
     * @param deadlineAt 原截止时间
     * @param finishedAt 执行终态时间
     */
    public record Run(String runId,String taskType,String status,long version,ValidationPlan plan,BudgetSnapshot budget,
            List<ValidationStepResult> results,boolean stopRequested,String errorCode,String errorSummary,String pendingExecutionId,
            Instant createdAt,Instant deadlineAt,Instant finishedAt) {}
    /** 运行停止命令回执，acceptedVersion是首次受理后版本，不是最终状态保证。 */
    public record Stop(String runId,long acceptedVersion,boolean stopRequested) {}
    /** 新发生索引：occurrenceId为发生；runId/caseIndex定位用例；assessmentId定位判定；targetSnapshotRef为当时快照；createdAt为发生时间。 */
    public record Occurrence(String occurrenceId,String findingId,String runId,int caseIndex,String assessmentId,String targetSnapshotRef,Instant createdAt) {}
}
