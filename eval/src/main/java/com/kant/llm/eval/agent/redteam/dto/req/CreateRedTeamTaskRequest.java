package com.kant.llm.eval.agent.redteam.dto.req;

import com.kant.agent.contract.probe.ExecutionMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 创建自动红队任务请求。
 *
 * <p>D02 使用单轮模式，D03 使用同一目标会话的多轮模式；两种模式共用任务
 * 配置入口，但服务端会根据目标快照分别校验所需能力。</p>
 */
@Getter
@Setter
public class CreateRedTeamTaskRequest {

    /** 创建任务幂等键。 */
    @NotBlank(message = "idempotencyKey不能为空")
    @Size(max = 128, message = "idempotencyKey长度不能超过128")
    private String idempotencyKey;

    /** 评测活动标识。 */
    @NotBlank(message = "activityId不能为空")
    @Size(max = 64, message = "activityId长度不能超过64")
    private String activityId;

    /** 已审批或预授权的运行授权引用。 */
    @NotBlank(message = "authorizationRef不能为空")
    @Size(max = 128, message = "authorizationRef长度不能超过128")
    private String authorizationRef;

    /** 现有 model_info 表中的目标模型 ID。 */
    @NotNull(message = "targetModelId不能为空")
    @Positive(message = "targetModelId必须大于0")
    private Long targetModelId;

    /** 当前任务使用的风险编码；D04 之前仍为单风险任务。 */
    @NotBlank(message = "riskId不能为空")
    @Size(max = 128, message = "riskId长度不能超过128")
    private String riskId;

    /** 判定规则版本。 */
    @NotBlank(message = "ruleSetVersion不能为空")
    @Size(max = 128, message = "ruleSetVersion长度不能超过128")
    private String ruleSetVersion;

    /** 任务交互模式；当前支持自适应单轮和自适应多轮。 */
    @NotNull(message = "executionMode不能为空")
    private ExecutionMode executionMode;

    /** 固定策略版本。 */
    @NotBlank(message = "strategyVersion不能为空")
    @Size(max = 128, message = "strategyVersion长度不能超过128")
    private String strategyVersion;

    /** 首轮发送给目标的种子输入。 */
    @NotBlank(message = "seedInput不能为空")
    @Size(max = 100000, message = "seedInput长度不能超过100000")
    private String seedInput;

    /** 代码侧用于生成疑似发现的明确字面信号。 */
    @NotEmpty(message = "riskIndicators不能为空")
    @Size(max = 20, message = "riskIndicators不能超过20项")
    private List<@NotBlank @Size(max = 256) String> riskIndicators;

    /** 固定预算策略。 */
    @NotNull(message = "budgetPolicy不能为空")
    @Valid
    private BudgetPolicyRequest budgetPolicy;
}
