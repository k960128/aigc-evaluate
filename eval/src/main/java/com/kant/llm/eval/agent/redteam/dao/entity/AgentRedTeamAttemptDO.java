package com.kant.llm.eval.agent.redteam.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 一个风险和策略组合下的攻击尝试事实。
 */
@Getter
@Setter
@TableName("agent_red_team_attempt")
public class AgentRedTeamAttemptDO {

    /** 数据库主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 尝试标识。 */
    private String attemptId;

    /** 所属任务。 */
    private String taskId;

    /** 所属运行。 */
    private String runId;

    /** 运行内序号。 */
    private Integer sequence;

    /** 风险编码。 */
    private String riskId;

    /** 固定策略版本。 */
    private String strategyVersion;

    /** 尝试状态，复用运行状态枚举语义。 */
    private String status;

    /** 已完成的探测数。 */
    private Integer probeCount;

    /** 停止原因。 */
    private String stopReason;

    /** 停止摘要。 */
    private String stopMessage;

    /** 开始时间。 */
    private LocalDateTime startedAt;

    /** 结束时间。 */
    private LocalDateTime finishedAt;

    /** 逻辑删除标识。 */
    @TableLogic
    private Boolean deleted;
}
