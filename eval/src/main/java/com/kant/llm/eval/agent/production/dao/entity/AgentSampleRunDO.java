package com.kant.llm.eval.agent.production.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 生产计划的一次执行实例，保存进度、预算账本、停止意图及Worker租约。
 * 完成只表示根槽位结束，不能推断全部质量通过；终态批次保留已有候选和调用事实。
 * 对应agent_sample_run表，不向外开放通用覆盖或删除接口；数据库时间约定为UTC微秒精度。
 */
@Getter
@Setter
@TableName("agent_sample_run")
public class AgentSampleRunDO {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 生产批次标识sprun命名空间。 */
    private String runId;
    /** 生产任务。 */
    private String taskId;
    /** 服务端租户。 */
    private String tenantId;
    /** 操作者。 */
    private String actorId;
    /** 任务内受理幂等键。 */
    private String idempotencyKey;
    /** 受理请求摘要。 */
    private String requestHash;
    /** manual键或精确UTC计划槽位，唯一防重复。 */
    private String triggerKey;
    /** 首次触发用途：MANUAL手动、SCHEDULED定时、BACKFILL补跑；共享槽位不改变原用途。 */
    private String triggerKind;
    /** 原计划UTC时刻，手工运行可为空。 */
    private LocalDateTime plannedFor;
    /** QUEUED排队、RUNNING执行、BLOCKED结果未知、STOPPED停止、COMPLETED槽位结束、FAILED失败。 */
    private String status;
    /** 批次控制版本。 */
    private Long version;
    /** Usage的JSON：agentCalls已受理次数、tokens已确认用量、reservedTokens未结算预留。 */
    private String usageJson;
    /** 当前待处理根槽位的0基序号，也等于已结束槽位数；不等于质量通过数量。 */
    private Integer ordinal;
    /** 当前槽位质量改写轮次：0为原生成，1或2为改写；推进新槽位后归零。 */
    private Integer rewriteRound;
    /** 当前改写父候选。 */
    private String parentCandidateId;
    /** 待确认或待结算生成操作；SUCCEEDED但未settle时仍保留，结算后显式清空。 */
    private String pendingOperationId;
    /** 停止新增调用的意图，不保证已发送的供应商请求立刻取消，也不释放未知预算。 */
    private Boolean stopRequested;
    /** 当前执行者标识，释放后为空；不同于操作表中固定的原发送者身份。 */
    private String workerId;
    /** UTC租约截止；BLOCKED释放执行者后仍可能保留短冷却时间，防止高频重复领取。 */
    private LocalDateTime leaseUntil;
    /** UTC固定期限，从受理起算且包含排队；Worker重启、恢复不会延长。 */
    private LocalDateTime deadlineAt;
    /** 稳定错误或停止编码。 */
    private String errorCode;
    /** UTC受理时间。 */
    private LocalDateTime createdAt;
    /** UTC确定终态时间；BLOCKED不是结束状态，该字段通常为空。 */
    private LocalDateTime finishedAt;
}
