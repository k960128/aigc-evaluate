package com.kant.llm.eval.agent.finding.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D04追加式复核，原判定不在此更新；不提供删除或覆盖接口。 */
@Getter @Setter @TableName("agent_finding_review")
public class AgentFindingReviewDO {
    @TableId(type=IdType.AUTO) private Long id;
    /** 全局复核标识。 */ private String reviewId;
    /** 当前发现。 */ private String findingId;
    /** 本发现内幂等键。 */ private String idempotencyKey;
    /** 规范化请求摘要，同键不同内容报冲突。 */ private String requestHash;
    /** 请求期望版本。 */ private Long expectedVersion;
    /** 首次受理后版本。 */ private Long acceptedVersion;
    /** EVIDENCE_CHECK或HUMAN。 */ private String kind;
    /** 独立意见。 */ private String opinion;
    /** 人工确认动作。 */ private String decision;
    /** 复核摘要，不保存目标原文。 */ private String reason;
    /** 本条意见证据引用。 */ private String evidenceRefsJson;
    /** 检查器版本和当时证据索引快照。 */ private String reportJson;
    /** 服务端复核操作者。 */ private String reviewerRef;
    /** 本次人工确认提交的严重度。 */ private String severity;
    /** 本次人工确认提交的负责人。 */ private String ownerRef;
    /** 受理前确认状态。 */ private String previousVerificationStatus;
    /** 受理后确认状态。 */ private String resultingVerificationStatus;
    /** 首次受理时间。 */ private LocalDateTime createdAt;
}
