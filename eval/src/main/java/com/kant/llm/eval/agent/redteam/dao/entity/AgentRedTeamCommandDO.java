package com.kant.llm.eval.agent.redteam.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D03命令受理事实，与运行控制意图在同一事务中写入。 */
@Getter @Setter @TableName("agent_red_team_command")
public class AgentRedTeamCommandDO {
    @TableId(type=IdType.AUTO) private Long id;
    /** 全局命令标识。 */ private String commandId;
    /** 所属运行。 */ private String runId;
    /** 本运行内幂等键。 */ private String idempotencyKey;
    /** 服务端操作者。 */ private String actorId;
    /** PAUSE、RESUME或STOP。 */ private String type;
    /** 请求携带的期望版本。 */ private Long expectedVersion;
    /** 受理后运行版本。 */ private Long acceptedVersion;
    /** 首次受理时间。 */ private LocalDateTime createdAt;
}
