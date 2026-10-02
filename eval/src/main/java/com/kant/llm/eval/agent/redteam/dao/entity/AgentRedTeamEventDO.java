package com.kant.llm.eval.agent.redteam.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D03追加式运行事件；数据库自增ID用于SSE断线续订，不包含证据正文。 */
@Getter @Setter @TableName("agent_red_team_event")
public class AgentRedTeamEventDO {
    @TableId(type=IdType.AUTO) private Long id;
    /** 所属运行。 */ private String runId;
    /** RUN_PROGRESS或RUN_COMMAND。 */ private String eventType;
    /** 当时运行状态。 */ private String status;
    /** 当时资源版本。 */ private Long runVersion;
    /** 可安全展示摘要。 */ private String summary;
    /** 事件创建时间。 */ private LocalDateTime createdAt;
}
