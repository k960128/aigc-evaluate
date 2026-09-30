package com.kant.llm.eval.agent.redteam.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 自动红队任务配置和汇总状态。
 *
 * <p>任务配置创建后不允许在原记录上修改目标、风险、策略和预算；再次运行
 * 应创建新的运行记录，配置修订应生成新的任务版本。</p>
 */
@Getter
@Setter
@TableName("agent_red_team_task")
public class AgentRedTeamTaskDO {

    /** 数据库主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 对外任务标识。 */
    private String taskId;

    /** 创建任务时使用的幂等键。 */
    private String idempotencyKey;

    /** 租户或业务空间。 */
    private String tenantId;

    /** 创建任务的操作者。 */
    private String actorId;

    /** 评测活动标识。 */
    private String activityId;

    /** 已审批或预授权的运行授权引用。 */
    private String authorizationRef;

    /** 目标类型。D02 当前为 MODEL。 */
    private String targetType;

    /** 目标资源标识。 */
    private String targetId;

    /** 创建任务时固定的目标快照引用。 */
    private String targetSnapshotRef;

    /** 风险编码。D02 当前只允许一项。 */
    private String riskId;

    /** 判定规则版本。 */
    private String ruleSetVersion;

    /** 任务层执行模式，D02 为 ADAPTIVE_SINGLE_TURN。 */
    private String executionMode;

    /** 固定策略版本。 */
    private String strategyVersion;

    /** 首轮攻击种子。 */
    private String seedInput;

    /** 规则侧疑似风险信号 JSON。 */
    private String riskIndicatorsJson;

    /** 不可变预算策略 JSON。 */
    private String budgetPolicyJson;

    /** 任务状态。 */
    private String status;

    /** 最近一次运行停止原因。 */
    private String stopReason;

    /** 最近一次运行停止摘要。 */
    private String stopMessage;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 首次启动时间。 */
    private LocalDateTime startedAt;

    /** 最近一次运行结束时间。 */
    private LocalDateTime finishedAt;

    /** 乐观版本号。 */
    private Long version;

    /** 逻辑删除标识。 */
    @TableLogic
    private Boolean deleted;
}
