package com.kant.llm.eval.agent.production.dto;

import com.kant.agent.contract.production.*;
import com.kant.agent.service.production.SampleQualityWorkflow;

import java.time.Instant;
import java.util.List;

/** 默认只返回配置/证据索引，受限种子与候选正文走独立认证接口。 */
public final class FactoryResponses {
    private FactoryResponses() {
    }

    /** seedId种子；version不可变1；name名称；contentHash原文摘要；sourceRef来源；createdAt形成时间。 */
    public record Seed(String seedId, long version, String name, String contentHash, String sourceRef,
                       Instant createdAt) {
    }

    /** taskType固定SAMPLE_PRODUCTION；spec/budget冻结配置；schedule计划；scheduleEnabled显式启停；nextFireAt实际UTC槽位；version控制版本。 */
    public record Task(String taskId, String taskType, String name, String activityId, String authorizationRef,
                       GenerationSpec spec, ProductionBudget budget,
                       ProductionSchedule schedule, boolean scheduleEnabled, Instant nextFireAt, long version,
                       Instant createdAt) {
    }

    /** runId批次；triggerKind触发用途；plannedFor原槽位；completedSlots已结束根槽位（不等于通过数）；pendingOperationId未确认操作。 */
    public record Run(String runId, String taskId, String status, long version, String triggerKind, Instant plannedFor,
                      int completedSlots, int rewriteRound,
                      String pendingOperationId, ProductionBudget.Usage usage, boolean stopRequested, String errorCode,
                      Instant createdAt, Instant deadlineAt, Instant finishedAt) {
    }

    /** ordinal从0开始；rewriteRound改写轮数；parentCandidateId血缘；quality独立质检；status独立人工审核/准入；version审核版本。 */
    public record Candidate(String candidateId, String taskId, String runId, String seedId, String operationId,
                            int ordinal, int rewriteRound, String parentCandidateId,
                            String contentHash, String duplicateOfCandidateId, Generation generation,
                            SampleQualityWorkflow.Report quality, String status, long version, Instant createdAt) {
    }

    /** status生成结果，与候选审核状态分开；reason分类原因；Token为单次操作计量；measurement区分供应商实报和估算。 */
    public record Generation(String status, String reason, long inputTokens, long outputTokens, String measurement) {
    }

    /** 操作元数据不带提示词、种子或响应正文；result为空表示未获得确认响应，不能据此推断零消耗。 */
    public record Operation(String operationId, int ordinal, int rewriteRound, String parentCandidateId, String status,
                            long reservationTokens,
                            Generation result, Instant createdAt) {
    }

    /** resourceId所属资源；content受限原文，纯文本展示；contentHash原文摘要，不按去重规则改写。 */
    public record Content(String resourceId, String content, String contentHash) {
    }

    /** reviewId审核；candidateId候选；decision动作；caseId/version仅APPROVE存在；acceptedVersion首次审核后候选版本。 */
    public record Review(String reviewId, String candidateId, String decision, String reason, String actorRef,
                         long acceptedVersion, String caseId, Long caseVersion, Instant createdAt) {
    }

    /** 正式SINGLE_MESSAGE配置；inputRef为已冻结候选ID；sourceFindingRef普通种子为空；approvalRef启用依据。 */
    public record Asset(String caseId, long version, String caseType, String inputRef, String inputHash,
                        String preconditions, String assertionDescription,
                        String riskId, String ruleSetVersion, String sourceFindingRef, String approvalRef,
                        Instant createdAt) {
    }

    /** caseSetId/version不可变集合；cases精确固定引用，可供后续标准评测路由读取。 */
    public record CaseSet(String caseSetId, long version, String name, List<FactoryRequests.CaseRef> cases,
                          Instant createdAt) {
    }

    /** 首次控制命令回执；enabled仅任务调度可用，stopRequested仅批次停止可用，二者不混用。 */
    public record Control(String resourceId, long acceptedVersion, Boolean enabled, Boolean stopRequested,
                          String reason) {
    }

    /** plannedFor原槽位/遗漏区间起点；kind触发或遗漏；runId批次；details审计JSON，UTC时间与时区保留。 */
    public record Fire(Instant plannedFor, String kind, String runId, String details, Instant createdAt) {
    }

    /** items本页；nextCursor下一页afterId；hasMore是否还有；列表不包含受限原文。 */
    public record Page<T>(List<T> items, long nextCursor, boolean hasMore) {
    }
}
