package com.kant.llm.eval.agent.finding.dto;
import com.kant.llm.eval.agent.redteam.dto.resp.SuspectedFindingResponse;
import java.util.List;
/**
 * D04发现详情：原始命中、当前确认、整改与复现分别呈现。
 * @param findingId 当前归并根标识；访问成员时详情返回根标识
 * @param originalOccurrence 原始SUSPECTED记录，不因后续确认而改写
 * @param targetSnapshotRefs 原始受影响目标快照，不替换成当前目标配置
 * @param ruleSetVersion 原始风险规则版本
 * @param originalAssessmentRef 原Assessment引用，D02/D03没有正式Judge时为空
 * @param originalJudgeVersion 原Judge版本，历史未记录时为空，不能推断填入检查器版本
 * @param verificationStatus 当前确认状态
 * @param remediationStatus 独立整改状态，首次确认后OPEN；未确认时为空
 * @param severity 当前严重度，默认PENDING
 * @param ownerRef 整改负责人引用，首次确认要求填写
 * @param version 下次修改填写的expectedVersion，无复核时为0
 * @param reproductionRefs 独立复现运行引用；空表示未申请，不代表复现失败
 * @param latestReviews 最近100条复核，按受理版本顺序展示
 * @param hasEarlierReviews 更早记录是否需通过reviews分页查询
 * @param occurrences 归并后所有原始发生，拆分不删除原证据
 * @param validationOccurrenceRefs 新复现/回归命中发生记录，与初始发生分别保存
 * @param currentRegressionRunId 当前指定回归，重新打开/变更用例时失效
 * @param repairSnapshotRef 当前指定修复快照，非任意历史通过目标
 * @param formalCaseRefs 正式用例版本引用，以caseId:version表示
 * @param requiredCases 当前回归冻结的完整正式版本范围；无当前回归时为空
 * @param resolvedBy 最近确认修复的认证负责人，重新打开时清空
 * @param resolutionReason 当前修复确认依据，接受风险依据独立保存在变更历史
 */
public record FindingDetailResponse(String findingId,SuspectedFindingResponse originalOccurrence,List<String> targetSnapshotRefs,
        String ruleSetVersion,String originalAssessmentRef,String originalJudgeVersion,String verificationStatus,
        String remediationStatus,String severity,String ownerRef,long version,List<String> reproductionRefs,
        List<FindingReviewResponse> latestReviews,boolean hasEarlierReviews,List<SuspectedFindingResponse> occurrences,
        List<String> validationOccurrenceRefs,String currentRegressionRunId,String repairSnapshotRef,List<String> formalCaseRefs,
        List<com.kant.llm.eval.agent.validation.dto.ValidationRequests.CaseRef> requiredCases,String resolvedBy,String resolutionReason) {}
