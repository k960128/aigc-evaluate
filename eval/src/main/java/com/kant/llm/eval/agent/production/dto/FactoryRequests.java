package com.kant.llm.eval.agent.production.dto;

import com.kant.agent.contract.production.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/** 服务端认证身份不出现在请求中；字段含义在DTO及D05接入文档共同维护。 */
public final class FactoryRequests {
    private FactoryRequests() {
    }

    /** idempotencyKey创建键；name展示名；content专家种子原文；sourceRef可追溯来源引用，不自动访问外部URL。 */
    public record Seed(@NotBlank @Size(max = 128) String idempotencyKey, @NotBlank @Size(max = 256) String name,
                       @NotBlank @Size(max = 10000) String content, @Size(max = 256) String sourceRef) {
    }

    /** name任务名；activityId活动；authorizationRef独立生产授权；spec固定生成配置；budget单批预算；schedule可空，创建后默认不启用。 */
    public record Task(@NotBlank @Size(max = 128) String idempotencyKey, @NotBlank @Size(max = 256) String name,
                       @NotBlank @Size(max = 64) String activityId, @NotBlank @Size(max = 128) String authorizationRef,
                       @NotNull @Valid GenerationSpec spec, @NotNull @Valid ProductionBudget budget,
                       @Valid ProductionSchedule schedule) {
    }

    /**
     * 基于已有计划创建新的冻结计划；旧计划不覆盖，新的生成角色快照由服务端重新读取。
     * idempotencyKey为新计划创建键；name为新计划名称。
     */
    public record ResetTask(@NotBlank @Size(max = 128) String idempotencyKey,
                            @NotBlank @Size(max = 256) String name) {
    }

    /** expectedVersion为任务版本；plannedFor为空表示手动，填写精确原槽位表示显式补跑，不刷新为当前时间。 */
    public record Run(@NotBlank @Size(max = 128) String idempotencyKey, @NotNull @Min(0) Long expectedVersion,
                      Instant plannedFor) {
    }

    /** 计划启停，expectedVersion来自任务，不影响已排队/在途批次；reason人工依据。 */
    public record ScheduleControl(@NotBlank @Size(max = 128) String idempotencyKey,
                                  @NotNull @Min(0) Long expectedVersion,
                                  @NotNull Boolean enabled, @NotBlank @Size(max = 2000) String reason) {
    }

    /** 停止批次，expectedVersion来自批次；不能保证供应商请求立即中断。 */
    public record Stop(@NotBlank @Size(max = 128) String idempotencyKey, @NotNull @Min(0) Long expectedVersion) {
    }

    /** 人工APPROVE/RETURN；审批必须明确质量检查不等于风险验证，expectedVersion来自候选。 */
    public record Review(@NotBlank @Size(max = 128) String idempotencyKey, @NotNull @Min(0) Long expectedVersion,
                         @NotNull Decision decision, @NotBlank @Size(max = 2000) String reason,
                         boolean acknowledgeQualityOnly) {
        public enum Decision {APPROVE, RETURN}
    }

    /** 正式不可变用例引用；不能传候选ID或latest。 */
    public record CaseRef(@NotBlank @Size(max = 96) String caseId, @Min(1) long version) {
    }

    /** 审核后正式集合快照，name展示名；cases全部精确正式版本，首版最多500条。 */
    public record CaseSet(@NotBlank @Size(max = 128) String idempotencyKey, @NotBlank @Size(max = 256) String name,
                          @NotEmpty @Size(max = 500) List<@NotNull @Valid CaseRef> cases) {
    }
}
