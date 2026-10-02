package com.kant.llm.eval.agent.redteam.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * D03 目标会话当前控制面状态。
 */
@Getter
@Setter
@TableName("agent_red_team_session")
public class AgentRedTeamSessionDO {

    /** 数据库主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 平台会话标识，同时作为 D01 sessionRef。 */
    private String sessionId;

    /** 所属任务。 */
    private String taskId;

    /** 所属运行。 */
    private String runId;

    /** 所属攻击尝试。 */
    private String attemptId;

    /** 业务空间。 */
    private String tenantId;

    /** 发起操作者。 */
    private String actorId;

    /** 会话绑定的目标快照。 */
    private String targetSnapshotRef;

    /** OPENING、ACTIVE、PAUSED、BLOCKED等会话状态。 */
    private String status;

    /** 已完成的目标消息轮数。 */
    private Integer turnCount;

    /** 下一条动作序号。 */
    private Integer nextSequence;

    /** 最近一次逻辑探测。 */
    private String lastProbeId;

    /** 最近一次物理执行。 */
    private String lastExecutionId;

    /** 是否收到暂停意图。 */
    private Boolean pauseRequested;

    /** 是否收到停止意图。 */
    private Boolean stopRequested;

    /** 最新检查点版本。 */
    private Long checkpointVersion;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 关闭时间。 */
    private LocalDateTime closedAt;

    /** 稳定错误编码。 */
    private String errorCode;

    /** 可安全展示的错误摘要。 */
    private String errorMessage;

    /** 逻辑删除标识。 */
    @TableLogic
    private Boolean deleted;
}
