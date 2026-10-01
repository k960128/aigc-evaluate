package com.kant.agent.contract.finding;

import com.kant.agent.contract.common.ContractChecks;
import java.util.List;

/**
 * 证据一致性检查结果。VALID仅证明索引、归属和摘要一致，不证明风险或复现成功。
 * @param integrity 引用一致性：VALID有效、INCOMPLETE缺失、INVALID不一致
 * @param opinion 检查器意见，只能建议复现或指出证据不足
 * @param checkerVersion 确定性检查器实现版本，不冒充原Judge版本
 * @param reason 可安全展示的检查说明
 * @param targetSnapshotRef 原始固定目标快照，不读取当前目标覆盖它
 * @param ruleSetVersion 原规则集版本
 * @param evidenceSnapshot 本次成功核对的轨迹索引；不含正文
 */
public record FindingVerificationReport(Integrity integrity,FindingReviewOpinion opinion,String checkerVersion,
        String reason,String targetSnapshotRef,String ruleSetVersion,List<FindingEvidenceSnapshot> evidenceSnapshot) {
    public enum Integrity {
        /** 一致性核对通过，仍需要规则复核。 */ VALID,
        /** 必需事实或证据缺失。 */ INCOMPLETE,
        /** 身份、轨迹、类型或摘要不一致。 */ INVALID
    }
    public FindingVerificationReport {
        integrity=ContractChecks.required(integrity,"integrity"); opinion=ContractChecks.required(opinion,"opinion");
        checkerVersion=ContractChecks.notBlank(checkerVersion,"checkerVersion"); reason=ContractChecks.notBlank(reason,"reason");
        targetSnapshotRef=ContractChecks.notBlank(targetSnapshotRef,"targetSnapshotRef");
        ruleSetVersion=ContractChecks.notBlank(ruleSetVersion,"ruleSetVersion"); evidenceSnapshot=List.copyOf(evidenceSnapshot);
        ContractChecks.require(integrity==Integrity.VALID
                ? !evidenceSnapshot.isEmpty() && opinion==FindingReviewOpinion.REPLAY_REQUIRED
                : opinion==FindingReviewOpinion.INSUFFICIENT,"一致性检查不能自行声明支持风险或复现成功");
    }
}
