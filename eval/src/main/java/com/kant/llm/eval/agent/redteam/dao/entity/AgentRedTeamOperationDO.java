package com.kant.llm.eval.agent.redteam.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * D03操作日志，commandJson与actionJson包含受限输入，不允许通过通用CRUD公开。
 * 与预算预留同事务写入；同运行、动作序号唯一，恢复时不重新生成输入。
 */
@Getter @Setter @TableName("agent_red_team_operation")
public class AgentRedTeamOperationDO {
    @TableId(type=IdType.AUTO) private Long id;
    /** 所属运行。 */ private String runId;
    /** 动作序号。 */ private Integer sequence;
    /** 固定动作标识。 */ private String actionId;
    /** 已校验攻击动作JSON，受限正文。 */ private String actionJson;
    /** 完整ProbeCommand，受限正文。 */ private String commandJson;
    /** 已预留上下文Token。 */ private Long reservedTokens;
    /** PREPARED、DISPATCHING、SETTLED。 */ private String status;
    /** 创建时间。 */ private LocalDateTime createdAt;
}
