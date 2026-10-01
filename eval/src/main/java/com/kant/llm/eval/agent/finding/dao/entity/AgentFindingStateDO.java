package com.kant.llm.eval.agent.finding.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D04可变确认与整改控制面，不更新D02原始发生表。 */
@Getter @Setter @TableName("agent_finding_state")
public class AgentFindingStateDO {
    @TableId(type=IdType.AUTO) private Long id;
    /** 归并根原始发生标识。 */ private String findingId;
    /** 服务端租户。 */ private String tenantId;
    /** 当前记录归属的操作者，沿用现有身份范围。 */ private String actorId;
    /** SUSPECTED、CONFIRMED、DISMISSED、INCONCLUSIVE。 */ private String verificationStatus;
    /** 独立整改状态；RESOLVED必须由负责人依据当前完整回归确认。 */ private String remediationStatus;
    /** PENDING、LOW、MEDIUM、HIGH、CRITICAL。 */ private String severity;
    /** 整改负责人引用。 */ private String ownerRef;
    /** 业务版本，复核受理时增加。 */ private Long version;
    /** 当前指定固定回归，旧通过记录不能替代。 */ private String currentRegressionRunId;
    /** 当前指定修复目标快照。 */ private String repairSnapshotRef;
    /** 当前全部正式用例固定版本。 */ private String requiredCasesJson;
    /** 最近确认修复的认证操作者。 */ private String resolvedBy;
    /** 负责人确认修复依据，重新打开时清空。 */ private String resolutionReason;
    /** 首次创建时间。 */ private LocalDateTime createdAt;
    /** 最近受理时间。 */ private LocalDateTime updatedAt;
}
