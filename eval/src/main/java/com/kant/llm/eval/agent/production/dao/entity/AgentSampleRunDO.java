package com.kant.llm.eval.agent.production.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D05agent_sample_run事实存储，不开放通用覆盖或删除接口。 */
@Getter @Setter @TableName("agent_sample_run")
public class AgentSampleRunDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 生产批次标识sprun命名空间。 */ private String runId;
    /** 生产任务。 */ private String taskId;
    /** 服务端租户。 */ private String tenantId;
    /** 操作者。 */ private String actorId;
    /** 任务内受理幂等键。 */ private String idempotencyKey;
    /** 受理请求摘要。 */ private String requestHash;
    /** manual键或精确UTC计划槽位，唯一防重复。 */ private String triggerKey;
    /** MANUAL SCHEDULED BACKFILL。 */ private String triggerKind;
    /** 原计划UTC时刻，手工运行可为空。 */ private LocalDateTime plannedFor;
    /** QUEUED RUNNING BLOCKED STOPPED COMPLETED FAILED。 */ private String status;
    /** 批次控制版本。 */ private Long version;
    /** 独立调用和Token账本。 */ private String usageJson;
    /** 下一个根槽位0基序号。 */ private Integer ordinal;
    /** 当前槽位质量改写轮次。 */ private Integer rewriteRound;
    /** 当前改写父候选。 */ private String parentCandidateId;
    /** 在途生成操作引用。 */ private String pendingOperationId;
    /** 停止意图。 */ private Boolean stopRequested;
    /** 租约持有者。 */ private String workerId;
    /** UTC租约截止。 */ private LocalDateTime leaseUntil;
    /** UTC原运行期限。 */ private LocalDateTime deadlineAt;
    /** 稳定错误或停止编码。 */ private String errorCode;
    /** UTC受理时间。 */ private LocalDateTime createdAt;
    /** UTC终态时间。 */ private LocalDateTime finishedAt;
}
