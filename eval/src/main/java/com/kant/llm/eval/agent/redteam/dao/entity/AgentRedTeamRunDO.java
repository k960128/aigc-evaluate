package com.kant.llm.eval.agent.redteam.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 自动红队一次运行的状态和预算摘要。
 */
@Getter
@Setter
@TableName("agent_red_team_run")
public class AgentRedTeamRunDO {

    /** 数据库主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 运行标识。 */
    private String runId;

    /** 所属任务标识。 */
    private String taskId;

    /** 租户或业务空间。 */
    private String tenantId;

    /** 操作者标识。 */
    private String actorId;

    /** QUEUED、RUNNING、BLOCKED、COMPLETED、FAILED 或 STOPPED。 */
    private String status;

    /** 已预留或已发生的目标调用数。 */
    private Long targetCalls;

    /** 已预留或已发生的 Agent 调用数。 */
    private Long agentCalls;

    /** 已创建的攻击尝试数。 */
    private Long attempts;

    /** 已结算或保守记账的 Token 数；当前 D02 使用估算计量。 */
    private Long tokens;

    /** 尚未完成结算的 Token 预留数。 */
    private Long reservedTokens;

    /** 已记录的动作数。 */
    private Long actionCount;

    /** 当前攻击尝试标识。 */
    private String currentAttemptId;

    /** 停止原因。 */
    private String stopReason;

    /** 停止摘要。 */
    private String stopMessage;

    /** 是否收到用户停止意图。 */
    private Boolean stopRequested;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 开始时间。 */
    private LocalDateTime startedAt;

    /** 结束时间。 */
    private LocalDateTime finishedAt;

    /** 乐观版本号。 */
    private Long version;

    /** 逻辑删除标识。 */
    @TableLogic
    private Boolean deleted;
}
