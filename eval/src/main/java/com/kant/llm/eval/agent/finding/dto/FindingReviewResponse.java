package com.kant.llm.eval.agent.finding.dto;
import com.kant.agent.contract.finding.FindingVerificationReport;
import java.time.Instant;
import java.util.List;
/**
 * 不可覆盖的复核记录，幂等重放返回首次受理记录。
 * @param reviewId 复核记录标识
 * @param findingId 被复核发现
 * @param kind EVIDENCE_CHECK确定性一致性检查、HUMAN人工意见
 * @param opinion 本条复核意见，不替代原判定
 * @param decision 本条人工确认动作；自动检查固定COMMENT_ONLY
 * @param reason 可审计的人工或检查器依据
 * @param evidenceRefs 本条意见引用的证据
 * @param report 检查器版本及当时完整轨迹索引快照
 * @param reviewerRef 发起检查/意见的服务端操作者，不冒充独立模型身份
 * @param severity 本次人工确认填写的严重度，其他意见为空
 * @param ownerRef 本次人工确认填写的负责人，保留历史输入而不读当前值替代
 * @param previousVerificationStatus 受理前确认状态
 * @param resultingVerificationStatus 受理后确认状态，不是最新状态保证
 * @param acceptedVersion 首次受理后的发现版本，可用作复核历史分页游标
 * @param createdAt 首次受理时间
 */
public record FindingReviewResponse(String reviewId,String findingId,String kind,String opinion,String decision,String reason,
        List<String> evidenceRefs,FindingVerificationReport report,String reviewerRef,String severity,String ownerRef,String previousVerificationStatus,
        String resultingVerificationStatus,long acceptedVersion,Instant createdAt) {}
