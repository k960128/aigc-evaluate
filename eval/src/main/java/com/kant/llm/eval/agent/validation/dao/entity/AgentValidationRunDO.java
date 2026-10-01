package com.kant.llm.eval.agent.validation.dao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** D04agent_validation_run持久化事实；无公开通用删除/覆盖接口。 */
@Getter @Setter @TableName("agent_validation_run")
public class AgentValidationRunDO {
    /** 数据库主键。 */ @TableId(type=IdType.AUTO) private Long id;
    /** 独立验证运行，vrun_命名空间。 */ private String runId;
    /** 服务端租户。 */ private String tenantId;
    /** 服务端操作者。 */ private String actorId;
    /** 归并发现。 */ private String findingId;
    /** REPLAY或REGRESSION，回归属于标准评测用途。 */ private String kind;
    /** 本发现和用途内幂等键。 */ private String idempotencyKey;
    /** 完整请求摘要。 */ private String requestHash;
    /** 不可变执行计划，仅保存输入引用。 */ private String planJson;
    /** QUEUED、RUNNING、BLOCKED、COMPLETED、FAILED、STOPPED。 */ private String status;
    /** 查询和停止命令资源版本。 */ private Long version;
    /** 已结算和预留预算。 */ private String budgetJson;
    /** 已确认步骤与追加判定引用。 */ private String resultsJson;
    /** 固定在途命令，含受限原文，不公开。 */ private String pendingCommandJson;
    /** 当前在途输入预留。 */ private Long pendingTokens;
    /** 已受理但尚未归档的物理执行引用，不含正文。 */ private String pendingExecutionId;
    /** 下一步在展开计划中的0基索引。 */ private Integer cursor;
    /** 当前Worker租约持有者。 */ private String workerId;
    /** 租约截止时间。 */ private LocalDateTime leaseUntil;
    /** 禁止新增调用的停止意图。 */ private Boolean stopRequested;
    /** 稳定错误或停止编码。 */ private String errorCode;
    /** 安全摘要。 */ private String errorSummary;
    /** 首次受理时间。 */ private LocalDateTime createdAt;
    /** 固定验证期限，恢复不延长。 */ private LocalDateTime deadlineAt;
    /** 执行终态时间。 */ private LocalDateTime finishedAt;
}
