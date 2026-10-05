package com.kant.llm.eval.agent.production.dto;

import com.kant.agent.contract.production.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/**
 * D05控制面请求契约。认证身份由服务端获取，请求不能覆盖租户、操作者或审核人。
 * 字符串长度使用校验注解约束，领域组合约束由嵌套契约构造器校验；不接受目标执行参数。
 */
public final class FactoryRequests {
    /** 仅作为请求类型命名空间，不创建容器实例。 */
    private FactoryRequests() {
    }

    /**
     * 创建不可变专家种子，不触发模型生成。
     * @param idempotencyKey 主体内种子创建键；相同操作重试复用，同键异参冲突
     * @param name 种子展示名，不是样本正文
     * @param content 专家原文，保留首尾空格和换行，不允许空白正文
     * @param sourceRef 可空来源引用，只存储不访问外部地址
     */
    public record Seed(@NotBlank @Size(max = 128) String idempotencyKey, @NotBlank @Size(max = 256) String name,
                       @NotBlank @Size(max = 10000) String content, @Size(max = 256) String sourceRef) {
    }

    /**
     * 创建冻结生产计划；保存后不自动启用调度或创建批次。
     * @param idempotencyKey 主体内计划创建键，与重置创建共享键空间
     * @param name 生产计划展示名
     * @param activityId 活动引用，当前仅保存不查询活动实体
     * @param authorizationRef 外部生产批准引用，当前仅记录，不校验批准到期或撤销
     * @param spec 冻结的种子、生成角色、变异和质量约束
     * @param budget 每个批次的预算，不是整个计划累计预算
     * @param schedule 可空每日计划；为空仅支持手动运行，非空仍须显式启用
     */
    public record Task(@NotBlank @Size(max = 128) String idempotencyKey, @NotBlank @Size(max = 256) String name,
                       @NotBlank @Size(max = 64) String activityId, @NotBlank @Size(max = 128) String authorizationRef,
                       @NotNull @Valid GenerationSpec spec, @NotNull @Valid ProductionBudget budget,
                       @Valid ProductionSchedule schedule) {
    }

    /**
     * 基于已有计划创建新的冻结计划；旧计划不覆盖，生成与评审的组合快照由服务端重新读取。
     * 原预算和调度日期原样复制，新计划默认关闭调度，旧计划状态不变。
     * @param idempotencyKey 主体内新计划创建键；同键重试返回首次创建计划，不再次刷新配置
     * @param name 新计划展示名，来源计划由URL路径指定
     */
    public record ResetTask(@NotBlank @Size(max = 128) String idempotencyKey,
                            @NotBlank @Size(max = 256) String name) {
    }

    /**
     * 手动创建批次或补跑原计划槽位；不同请求键的同槽位补跑共享原批次。
     * @param idempotencyKey 计划内运行请求键，重试同一请求时复用
     * @param expectedVersion 从计划详情获取的控制版本，不是上一次批次的版本
     * @param plannedFor 可空原UTC槽位；空为MANUAL，非空为BACKFILL且必须已到期并匹配计划
     */
    public record Run(@NotBlank @Size(max = 128) String idempotencyKey, @NotNull @Min(0) Long expectedVersion,
                      Instant plannedFor) {
    }

    /**
     * 显式控制每日调度，不影响既有批次，也不重置调度游标。
     * @param idempotencyKey 该计划内控制命令键，重试时复用
     * @param expectedVersion 计划当前控制版本，不能使用Run.version
     * @param enabled true启用后续调度，false关闭后续调度
     * @param reason 人工启停依据，不应填入凭据或受限正文
     */
    public record ScheduleControl(@NotBlank @Size(max = 128) String idempotencyKey,
                                  @NotNull @Min(0) Long expectedVersion,
                                  @NotNull Boolean enabled, @NotBlank @Size(max = 2000) String reason) {
    }

    /**
     * 请求停止新增调用，不能保证已发出的供应商请求立即中断。
     * @param idempotencyKey 批次内停止命令键
     * @param expectedVersion 当前批次版本，应通过GET查询，不能使用Task.version
     */
    public record Stop(@NotBlank @Size(max = 128) String idempotencyKey, @NotNull @Min(0) Long expectedVersion) {
    }

    /**
     * 人工准入决定；只接受未重复、质量通过且仍待审核的候选。
     * @param idempotencyKey 候选内审核键，网络重试须复用原键和原请求
     * @param expectedVersion 当前候选版本，不是计划或批次版本
     * @param decision APPROVE批准生成正式资产，RETURN退回且不自动改写
     * @param reason 必填人工审核依据
     * @param acknowledgeQualityOnly 是否承认质检不代表目标风险已验证；批准时必须为true
     */
    public record Review(@NotBlank @Size(max = 128) String idempotencyKey, @NotNull @Min(0) Long expectedVersion,
                         @NotNull Decision decision, @NotBlank @Size(max = 2000) String reason,
                         boolean acknowledgeQualityOnly) {
        /** APPROVE只批准样本准入；RETURN只保留退回意见，二者均不是目标安全结论。 */
        public enum Decision {APPROVE, RETURN}
    }

    /**
     * 正式不可变用例引用，不能传候选ID或latest别名。
     * @param caseId 已经人工批准生成的正式用例ID
     * @param version 用例精确版本，至少1；当前正式资产初始版本为1
     */
    public record CaseRef(@NotBlank @Size(max = 96) String caseId, @Min(1) long version) {
    }

    /**
     * 人工选择正式资产组成固定集合，不自动启动标准评测。
     * @param idempotencyKey 主体内集合创建键，同键不得改变名称或用例顺序
     * @param name 固定用例集展示名
     * @param cases 有序正式版本引用，1至500条，不允许null或重复的ID与版本组合
     */
    public record CaseSet(@NotBlank @Size(max = 128) String idempotencyKey, @NotBlank @Size(max = 256) String name,
                          @NotEmpty @Size(max = 500) List<@NotNull @Valid CaseRef> cases) {
    }
}
