package com.kant.llm.eval.agent.finding.dto;
import com.kant.agent.contract.finding.*;
import jakarta.validation.constraints.*;
import java.util.List;
/**
 * 追加人工意见；审核人从服务端登录上下文获取。
 * @param idempotencyKey 本发现内幂等键，与自动复核共用唯一范围
 * @param expectedVersion 客户端已读到的发现版本
 * @param opinion SUPPORT支持、OPPOSE反对、INSUFFICIENT不足、REPLAY_REQUIRED建议复现
 * @param decision COMMENT_ONLY只留意见、CONFIRM确认、DISMISS驳回、INCONCLUSIVE待定
 * @param reason 人工依据，不得填写内部思维链或凭据
 * @param evidenceRefs 引用本发现原始证据的非空子集，不能跨运行指定新证据
 * @param severity 仅CONFIRM必填且不能PENDING，其他决策不能修改严重度
 * @param ownerRef 仅CONFIRM必填的整改负责人引用；暂不等于已完成通知或权限分配
 */
public record FindingHumanReviewRequest(
        @NotBlank @Size(max=128) @Pattern(regexp="[A-Za-z0-9_.:-]+") String idempotencyKey,
        @NotNull @Min(0) Long expectedVersion,@NotNull FindingReviewOpinion opinion,@NotNull Decision decision,
        @NotBlank @Size(max=2000) String reason,
        @NotEmpty @Size(max=1000) List<@NotBlank @Size(max=96) String> evidenceRefs,
        FindingSeverity severity,@Size(max=128) String ownerRef) {
    /** 意见与人工确认动作分离；评论不会修改确认状态。 */
    public enum Decision { COMMENT_ONLY, CONFIRM, DISMISS, INCONCLUSIVE }
}
