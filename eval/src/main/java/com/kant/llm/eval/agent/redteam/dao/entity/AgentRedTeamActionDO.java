package com.kant.llm.eval.agent.redteam.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 攻击 Agent 动作及其目标执行引用。
 *
 * <p>输入正文和响应正文不在本表重复保存，只记录摘要和 D01 证据引用。</p>
 */
@Getter
@Setter
@TableName("agent_red_team_action")
public class AgentRedTeamActionDO {

    /** 数据库主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 动作标识。 */
    private String actionId;

    /** 所属任务。 */
    private String taskId;

    /** 所属运行。 */
    private String runId;

    /** 所属尝试。 */
    private String attemptId;

    /** 尝试内序号。 */
    private Integer sequence;

    /** SEND_INPUT 或 END_ATTEMPT。 */
    private String actionType;

    /** 动作使用的策略版本。 */
    private String strategyVersion;

    /** 输入正文 SHA-256 摘要。 */
    private String inputHash;

    /** 面向审计的简短动作依据。 */
    private String reasonSummary;

    /** 支撑动作的观察引用 JSON。 */
    private String observationRefsJson;

    /** Agent 建议的停止原因。 */
    private String suggestedStopReason;

    /** 编排器最终确认的停止原因。 */
    private String actualStopReason;

    /** D01 输入证据引用。 */
    private String inputEvidenceRef;

    /** 逻辑探测标识。 */
    private String probeId;

    /** 物理执行标识。 */
    private String executionId;

    /** D01 目标执行状态。 */
    private String executionStatus;

    /** D01 响应证据引用。 */
    private String responseEvidenceRef;

    /** D01 错误编码。 */
    private String errorCode;

    /** 是否命中任务配置的疑似风险信号。 */
    private Boolean riskObserved;

    /** PROPOSED、EXECUTED、EXECUTION_UNKNOWN、REJECTED、STOPPED 或 ENDED。 */
    private String actionStatus;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 逻辑删除标识。 */
    @TableLogic
    private Boolean deleted;
}
