package com.kant.llm.eval.agent.validation.dto;
import com.kant.agent.contract.budget.BudgetPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
/** D04候选/验证请求。租户、审核人与调用身份均由服务端获取。 */
public final class ValidationRequests {
    private ValidationRequests() {}
    /**
     * @param idempotencyKey 本发现内创建幂等键
     * @param expectedFindingVersion 发现控制版本
     * @param sourceOccurrenceId 选择哪次原始发生，必须属于本发现
     * @param preconditions 人工可检查的原环境、测试身份、数据和会话准备说明
     * @param existingCaseId 修订既有用例时填写，首次创建为空
     * @param baseCaseVersion 修订依据版本，首次创建为空
     */
    public record Candidate(@NotBlank @Size(max=128) String idempotencyKey,@NotNull @Min(0) Long expectedFindingVersion,
            @NotBlank @Size(max=96) String sourceOccurrenceId,@NotBlank @Size(max=4000) String preconditions,
            @Size(max=96) String existingCaseId,@Min(1) Long baseCaseVersion) {}
    /**
     * @param idempotencyKey 本候选内审核幂等键
     * @param expectedVersion 候选版本
     * @param expectedFindingVersion 关联发现版本，防止旧意见覆盖新状态
     * @param decision APPROVE通过生成正式版本；RETURN退回，历史意见保留
     * @param reason 审核依据
     * @param acknowledgeTextOnlyCriterion 明确接受当前仅文本信号断言的范围，APPROVE必须true
     */
    public record CaseReview(@NotBlank @Size(max=128) String idempotencyKey,@NotNull @Min(0) Long expectedVersion,
            @NotNull @Min(0) Long expectedFindingVersion,@NotNull Decision decision,@NotBlank @Size(max=2000) String reason,
            boolean acknowledgeTextOnlyCriterion) {
        public enum Decision { APPROVE, RETURN }
    }
    /** 固定正式用例引用：caseId标识、version不可变版本，不能使用latest别名。 */
    public record CaseRef(@NotBlank @Size(max=96) String caseId,@Min(1) long version) {}
    /**
     * @param idempotencyKey 本发现和验证用途内幂等键
     * @param expectedFindingVersion 当前发现版本
     * @param sourceOccurrenceId 复现时选择原发生；回归为空
     * @param preconditions 复现的前置说明；回归采用正式用例冻结说明
     * @param expectedTargetSnapshotRef 当前目标快照前置条件；复现必须等于原快照，回归必须新快照
     * @param authorizationRef 本次独立验证授权引用
     * @param budgetPolicy 单独批准的验证预算，费用硬预算暂不支持
     * @param preconditionsAcknowledged 人工已准备并核对前置条件，不代表平台自动准备了应用夹具
     * @param cases 回归时所有当前正式用例的固定版本；复现为空
     */
    public record Run(@NotBlank @Size(max=128) String idempotencyKey,@NotNull @Min(0) Long expectedFindingVersion,
            @Size(max=96) String sourceOccurrenceId,@Size(max=4000) String preconditions,
            @NotBlank @Size(max=160) String expectedTargetSnapshotRef,@NotBlank @Size(max=128) String authorizationRef,
            @NotNull @Valid BudgetPolicy budgetPolicy,boolean preconditionsAcknowledged,
            @Size(max=20) List<@Valid CaseRef> cases) {}
    /** 停止验证：幂等键和运行expectedVersion；不保证中断已发外部请求。 */
    public record Stop(@NotBlank @Size(max=128) String idempotencyKey,@NotNull @Min(0) Long expectedVersion) {}
}
