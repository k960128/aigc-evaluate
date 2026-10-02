package com.kant.llm.eval.agent.probe.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 动态探测的物理执行事实。
 *
 * <p>该表不替代现有批量评测结果表，只承载自动红队动态输入所需的受理、
 * 执行、证据引用和消耗事实。原始输入和响应保存在独立证据表中。</p>
 */
@Getter
@Setter
@TableName("agent_probe_execution")
public class AgentProbeExecutionDO {

    /** 数据库自增主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务空间或租户标识。 */
    private String tenantId;

    /** 发起探测的操作者标识。 */
    private String actorId;

    /** 评测活动标识。 */
    private String activityId;

    /** 评测任务标识。 */
    private String taskId;

    /** 本次运行标识。 */
    private String runId;

    /** 攻击尝试标识。 */
    private String attemptId;

    /** 逻辑探测标识。 */
    private String probeId;

    /** 本次物理执行的唯一标识。 */
    private String executionId;

    /** 技术链路追踪标识。 */
    private String traceId;

    /** 本次执行所依据的授权引用。 */
    private String authorizationRef;

    /** 调用方提供的幂等键。 */
    private String idempotencyKey;

    /** 除技术追踪字段外的规范化请求指纹。 */
    private String requestFingerprint;

    /** 被测目标类型。 */
    private String targetType;

    /** 现有目标资源标识。 */
    private String targetId;

    /** 不可变目标快照引用。 */
    private String targetSnapshotRef;

    /** 不含凭据的目标快照 JSON，用于历史复现和审计。 */
    private String targetSnapshotJson;

    /** 当前探测对应的风险编码。 */
    private String riskId;

    /** 后续判定应使用的规则集版本。 */
    private String ruleSetVersion;

    /** 静态单轮、自适应单轮或自适应多轮模式。 */
    private String executionMode;

    /** 调用方明确要求的额外能力，按名称排序并以逗号分隔。 */
    private String requiredCapabilities;

    /** 目标会话引用；D01 单轮模型探测为空。 */
    private String sessionRef;

    /** 最晚开始目标调用时间；调用方未设置时为空。 */
    private LocalDateTime deadlineAt;

    /** 原始输入证据引用。 */
    private String inputEvidenceRef;

    /** 目标响应证据引用。 */
    private String responseEvidenceRef;

    /** 受控错误证据引用。 */
    private String errorEvidenceRef;

    /** ACCEPTED、RUNNING、SUCCEEDED、FAILED、UNKNOWN 等执行状态。 */
    private String status;

    /** 稳定的执行错误编码。 */
    private String errorCode;

    /** 可安全展示的执行错误摘要。 */
    private String errorMessage;

    /** COMPLETE、PARTIAL 或 MISSING。 */
    private String evidenceCompleteness;

    /** 本次目标调用耗时，单位毫秒。 */
    private Long latencyMs;

    /** 实际进入目标客户端调用的次数。 */
    private Long targetCalls;

    /** 本次执行层产生的 Agent 模型调用次数。 */
    private Long agentCalls;

    /** 输入 Token；底层无法提供时为空。 */
    private Long inputTokens;

    /** 输出 Token；底层无法提供时为空。 */
    private Long outputTokens;

    /** 实际或估算费用；无法取得时为空。 */
    private BigDecimal cost;

    /** 费用币种；费用为空时也为空。 */
    private String currency;

    /** ACTUAL、ESTIMATED 或 UNAVAILABLE。 */
    private String usageMeasurement;

    /** 是否已经记录取消意图。 */
    private Boolean cancellationRequested;

    /** 首次记录取消意图的时间。 */
    private LocalDateTime cancellationRequestedAt;

    /** 执行意图首次持久化时间。 */
    private LocalDateTime acceptedAt;

    /** 执行层开始处理时间。 */
    private LocalDateTime startedAt;

    /** 执行进入终态的时间。 */
    private LocalDateTime finishedAt;

    /** 状态并发控制版本号。 */
    private Long version;

    /** 记录创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 记录最后更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** MyBatis-Plus 逻辑删除标识。 */
    @TableLogic
    private Boolean deleted;
}
