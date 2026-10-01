package com.kant.llm.eval.agent.finding.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D04agent_finding_occurrence持久化事实；无公开通用删除/覆盖接口。 */
@Getter @Setter @TableName("agent_finding_occurrence")
public class AgentFindingOccurrenceDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 原始发生标识，唯一且不改原记录。 */ private String occurrenceId;
    /** 当前归并根发现。 */ private String findingId;
    /** 服务端租户。 */ private String tenantId;
    /** 服务端归属操作者。 */ private String actorId;
}
