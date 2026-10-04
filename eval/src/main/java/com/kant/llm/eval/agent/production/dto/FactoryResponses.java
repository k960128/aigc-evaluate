package com.kant.llm.eval.agent.production.dto;

import com.kant.agent.contract.production.*;
import com.kant.agent.service.production.SampleQualityWorkflow;

import java.time.Instant;
import java.util.List;

/** 默认只返回配置/证据索引，受限种子与候选正文走独立认证接口。 */
public final class FactoryResponses {
    /** 仅作为响应类型命名空间；各记录用于Result.data，不单独创建容器。 */
    private FactoryResponses() {
    }

    /**
     * 种子元数据，原文通过独立认证接口获取。
     * @param seedId 专家种子业务标识
     * @param version 不可变种子版本，当前固定1
     * @param name 展示名称
     * @param contentHash 原文SHA-256，不是去重规范化后的摘要
     * @param sourceRef 可空来源引用，不自动访问该引用
     * @param createdAt 种子创建UTC时间
     */
    public record Seed(String seedId, long version, String name, String contentHash, String sourceRef,
                       Instant createdAt) {
    }

    /**
     * 生产计划定义及当前控制状态；重新执行不会刷新冻结配置。
     * @param taskId 生产计划ID，不是运行批次ID
     * @param taskType 固定SAMPLE_PRODUCTION，区别于红队探索任务
     * @param name 计划展示名
     * @param activityId 原活动引用，当前不验证活动实体
     * @param authorizationRef 原生产批准引用，当前只记录不校验外部审批
     * @param spec 冻结生成约束，包含种子版本与生成角色指纹
     * @param budget 每个批次独立的调用、Token及期限预算
     * @param schedule 可空每日调度定义，不因启停而修改
     * @param scheduleEnabled 当前是否显式开启定时生产
     * @param nextFireAt 下一个原UTC槽位；无调度或范围耗尽可为空
     * @param version 计划控制版本，启停递增；技术游标推进通常不递增
     * @param createdAt 计划创建UTC时间
     */
    public record Task(String taskId, String taskType, String name, String activityId, String authorizationRef,
                       GenerationSpec spec, ProductionBudget budget,
                       ProductionSchedule schedule, boolean scheduleEnabled, Instant nextFireAt, long version,
                       Instant createdAt) {
    }

    /**
     * 一次批次的执行快照；POST回放首次受理结果，GET用于获取当前进度。
     * @param runId 生产批次ID
     * @param taskId 来源冻结计划ID
     * @param status QUEUED排队、RUNNING执行、BLOCKED待确认、STOPPED停止、COMPLETED槽位结束、FAILED失败
     * @param version 批次控制和推进版本，停止命令使用此值
     * @param triggerKind 首次触发类型：MANUAL手动、SCHEDULED定时、BACKFILL补跑
     * @param plannedFor 原UTC调度槽位，纯手动为空，不是实际调用时刻
     * @param completedSlots 已结束根槽位数，不等于候选数、合格数或正式资产数
     * @param rewriteRound 当前槽位改写轮次，0表示首轮生成
     * @param pendingOperationId 可空待确认或待结算操作ID，不意味着允许重发
     * @param usage 调用次数、已确认Token及未结算Token预留账本
     * @param stopRequested 是否请求停止新增调用，不是远端取消成功证明
     * @param errorCode 可空结束/阻塞原因，正常完成为空
     * @param createdAt 批次受理UTC时间
     * @param deadlineAt 从受理起算的固定期限，包含排队，恢复不延长
     * @param finishedAt 可空确定终态时间；BLOCKED不是确定终态
     */
    public record Run(String runId, String taskId, String status, long version, String triggerKind, Instant plannedFor,
                      int completedSlots, int rewriteRound,
                      String pendingOperationId, ProductionBudget.Usage usage, boolean stopRequested, String errorCode,
                      Instant createdAt, Instant deadlineAt, Instant finishedAt) {
    }

    /**
     * 候选索引；生成状态、规则质量与人工准入分别表达，不能混作同一个成功标志。
     * @param candidateId 由生成操作身份确定的候选ID
     * @param taskId 原生产计划ID
     * @param runId 形成该候选的批次ID
     * @param seedId 原专家种子ID，精确版本在计划spec中
     * @param operationId 产生该候选的唯一物理操作ID
     * @param ordinal 0基根槽位序号，不是分页游标
     * @param rewriteRound 0为初始生成，1或2为质量反馈改写
     * @param parentCandidateId 可空上一轮候选ID，改写不覆盖父候选
     * @param contentHash 可空原文摘要，正文需单独读取
     * @param duplicateOfCandidateId 可空首个同规范键候选ID，不表示语义相似或风险归并
     * @param generation 生成分类与用量，不含响应正文
     * @param quality 确定性质检报告，不是目标安全判定
     * @param status PENDING_REVIEW待审、QUALITY_REJECTED质检拒绝、DUPLICATE重复、APPROVED批准、RETURNED退回
     * @param version 候选审核版本，形成时0，审核后递增
     * @param createdAt 候选落库UTC时间
     */
    public record Candidate(String candidateId, String taskId, String runId, String seedId, String operationId,
                            int ordinal, int rewriteRound, String parentCandidateId,
                            String contentHash, String duplicateOfCandidateId, Generation generation,
                            SampleQualityWorkflow.Report quality, String status, long version, Instant createdAt) {
    }

    /**
     * 单次模型响应的安全元数据。
     * @param status GENERATED结构合法、REFUSED结构化拒绝、INVALID结构非法；不猜测非结构化文本意图
     * @param reason 适配器固定分类原因，不原样返回模型思维链或非法响应
     * @param inputTokens 本次输入Token计量
     * @param outputTokens 本次输出Token计量，拒绝/非法响应也可能有消耗
     * @param measurement ACTUAL表示供应商实报，ESTIMATED表示平台估算
     */
    public record Generation(String status, String reason, long inputTokens, long outputTokens, String measurement) {
    }

    /**
     * 固定生成操作元数据，不带提示词、种子或响应正文。
     * @param operationId 固定物理操作ID，恢复不会另换ID重新发送
     * @param ordinal 原0基根槽位
     * @param rewriteRound 原改写轮次
     * @param parentCandidateId 可空上一轮候选
     * @param status ACCEPTED预留、CALLING发送中、SUCCEEDED响应已确认、UNKNOWN结果未知、CANCELLED未发出已取消
     * @param reservationTokens 原始预留Token，结算不覆盖这个历史字段
     * @param result 可空确认响应元数据；空不能推断零消耗或允许重发
     * @param createdAt 首次预留UTC时间
     */
    public record Operation(String operationId, int ordinal, int rewriteRound, String parentCandidateId, String status,
                            long reservationTokens,
                            Generation result, Instant createdAt) {
    }

    /**
     * 独立认证接口返回的受限正文，前端必须按不可信纯文本展示。
     * @param resourceId 本次查询的种子、候选或正式资产ID
     * @param content 原始正文；非法生成或拒绝生成的候选可为空
     * @param contentHash 可空原文SHA-256，不经过去重规范化
     */
    public record Content(String resourceId, String content, String contentHash) {
    }

    /**
     * 首次人工审核回执，不因重试而重做审核。
     * @param reviewId 审核记录ID，同时是正式资产的approvalRef
     * @param candidateId 被审核候选ID
     * @param decision APPROVE批准或RETURN退回，不是目标安全结论
     * @param reason 人工审核依据
     * @param actorRef 服务端认证审核主体，不能由客户端指定
     * @param acceptedVersion 首次审核成功后的候选版本
     * @param caseId 仅批准时存在的正式用例ID
     * @param caseVersion 仅批准时存在的正式版本，当前为1
     * @param createdAt 审核受理UTC时间
     */
    public record Review(String reviewId, String candidateId, String decision, String reason, String actorRef,
                         long acceptedVersion, String caseId, Long caseVersion, Instant createdAt) {
    }

    /**
     * 人工准入后的不可变正式资产定义，不自动接入旧流水线或证明目标命中。
     * @param caseId 正式用例ID，不等于candidateId
     * @param version 精确正式版本，当前初始为1，不支持latest
     * @param caseType 当前固定SINGLE_MESSAGE，不能冒充多轮会话
     * @param inputRef 来源已批准候选ID，不是外部URL
     * @param inputHash 冻结原文SHA-256，用于核对读取内容
     * @param preconditions 专家冻结的执行前置条件
     * @param assertionDescription 专家断言说明，不是当前可执行DSL
     * @param riskId 专家指定风险类别，不是模型生成的风险结论
     * @param ruleSetVersion 原计划规则版本引用
     * @param sourceFindingRef 普通种子生产为空，不编造目标风险发现来源
     * @param approvalRef 人工批准记录ID
     * @param createdAt 正式资产形成UTC时间
     */
    public record Asset(String caseId, long version, String caseType, String inputRef, String inputHash,
                        String preconditions, String assertionDescription,
                        String riskId, String ruleSetVersion, String sourceFindingRef, String approvalRef,
                        Instant createdAt) {
    }

    /**
     * 冻结的有序正式用例集合，创建集合不执行目标评测。
     * @param caseSetId 正式集合ID，不能直接当作旧dataset ID使用
     * @param version 不可变集合版本，当前为1
     * @param name 集合展示名
     * @param cases 正式用例ID与精确版本的有序引用
     * @param createdAt 集合形成UTC时间
     */
    public record CaseSet(String caseSetId, long version, String name, List<FactoryRequests.CaseRef> cases,
                          Instant createdAt) {
    }

    /**
     * 首次控制命令回执，重试只回放，不代表资源此刻仍处于相同版本。
     * @param resourceId 被控制的计划或批次ID
     * @param acceptedVersion 首次命令生效后的资源版本
     * @param enabled 仅计划启停使用，批次停止时为空
     * @param stopRequested 仅批次停止使用，计划启停时为空
     * @param reason 人工启停依据或停止边界的固定说明
     */
    public record Control(String resourceId, long acceptedVersion, Boolean enabled, Boolean stopRequested,
                          String reason) {
    }

    /**
     * 调度触发或遗漏审计，不将漏触发伪装成已经生成。
     * @param plannedFor 原UTC槽位；遗漏区间记录中为包含的起点
     * @param kind QUEUED已排队、MISSED单槽位遗漏、MISSED_RANGE较早槽位区间遗漏
     * @param runId 已关联批次ID，遗漏时为空
     * @param details 审计JSON字符串，可包含zoneId、原因、策略或from/before区间（左闭右开）
     * @param createdAt 实际记录审计的UTC时间，不是原计划时刻
     */
    public record Fire(Instant plannedFor, String kind, String runId, String details, Instant createdAt) {
    }

    /**
     * 基于数据库主键的升序游标页，不保证多次查询间数据静止。
     * @param items 本页索引记录，不含受限正文
     * @param nextCursor 下一次请求的afterId；空页保留传入游标
     * @param hasMore 本次查询时是否还有记录，不保证以后不会新增
     * @param <T> 具体元数据响应类型
     */
    public record Page<T>(List<T> items, long nextCursor, boolean hasMore) {
    }
}
