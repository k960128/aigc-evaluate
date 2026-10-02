package com.kant.llm.eval.agent.redteam.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * D03 会话追加式检查点。
 */
@Getter
@Setter
@TableName("agent_red_team_session_checkpoint")
public class AgentRedTeamSessionCheckpointDO {

    /** 数据库主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 检查点标识。 */
    private String checkpointId;

    /** 所属会话。 */
    private String sessionId;

    /** 所属任务。 */
    private String taskId;

    /** 所属运行。 */
    private String runId;

    /** 所属尝试。 */
    private String attemptId;

    /** 会话内单调递增版本。 */
    private Long checkpointVersion;

    /** 已完成轮数。 */
    private Integer turnCount;

    /** 下一条动作序号。 */
    private Integer nextSequence;

    /** 最近一次探测。 */
    private String lastProbeId;

    /** 最近一次物理执行。 */
    private String lastExecutionId;

    /** 已确认历史摘要。 */
    private String historyHash;

    /** 保存时的会话状态。 */
    private String status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 逻辑删除标识。 */
    @TableLogic
    private Boolean deleted;
}
