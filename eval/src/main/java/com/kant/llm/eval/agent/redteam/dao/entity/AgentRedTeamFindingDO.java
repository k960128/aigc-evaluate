package com.kant.llm.eval.agent.redteam.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * D02 产生的疑似风险发现。
 */
@Getter
@Setter
@TableName("agent_red_team_finding")
public class AgentRedTeamFindingDO {

    /** 数据库主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 发现标识。 */
    private String findingId;

    /** 所属任务。 */
    private String taskId;

    /** 所属运行。 */
    private String runId;

    /** 所属尝试。 */
    private String attemptId;

    /** 触发发现的动作。 */
    private String actionId;

    /** 风险编码。 */
    private String riskId;

    /** 输入、响应和判定依据引用 JSON。 */
    private String evidenceRefsJson;

    /** 规则信号命中的说明。 */
    private String reason;

    /** D02 固定为 SUSPECTED。 */
    private String verificationStatus;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 逻辑删除标识。 */
    @TableLogic
    private Boolean deleted;
}
