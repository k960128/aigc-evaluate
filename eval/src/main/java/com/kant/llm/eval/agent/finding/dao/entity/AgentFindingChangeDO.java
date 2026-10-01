package com.kant.llm.eval.agent.finding.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D04agent_finding_change持久化事实；无公开通用删除/覆盖接口。 */
@Getter @Setter @TableName("agent_finding_change")
public class AgentFindingChangeDO {
    /** 本次受理后的根发现版本，用于变更历史游标。 */ private Long acceptedVersion;
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 变更审计标识。 */ private String changeId;
    /** 命令作用的根发现。 */ private String findingId;
    /** 本发现内变更幂等键。 */ private String idempotencyKey;
    /** 包含命令版本的请求摘要。 */ private String requestHash;
    /** 命令引用及版本快照，不包含目标原文。 */ private String requestJson;
    /** MERGE、SPLIT或整改命令。 */ private String type;
    /** 人工依据，保留历史。 */ private String reason;
    /** 服务端操作者。 */ private String actorId;
    /** 首次受理回执快照。 */ private String resultJson;
    /** 首次受理时间。 */ private LocalDateTime createdAt;
}
