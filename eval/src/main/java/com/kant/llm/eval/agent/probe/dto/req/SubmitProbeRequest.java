package com.kant.llm.eval.agent.probe.dto.req;

import com.kant.agent.contract.probe.ExecutionMode;
import com.kant.agent.contract.target.TargetCapability;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;

/**
 * 提交一次受控模型探测的请求。
 *
 * <p>租户和操作者由服务端登录态取得，调用方不能在请求中指定。D01 只接受
 * {@link ExecutionMode#STATIC_SINGLE_TURN}，其他模式会返回能力不支持错误。</p>
 */
@Getter
@Setter
public class SubmitProbeRequest {

    /**
     * 调用方幂等键。同一租户内使用同一键和相同参数会返回原执行；
     * 同一键对应不同参数时返回冲突。
     */
    @NotBlank(message = "idempotencyKey不能为空")
    @Size(max = 128, message = "idempotencyKey长度不能超过128")
    private String idempotencyKey;

    /** 逻辑探测标识，用于把未来可能的多次物理执行归到同一探测。 */
    @NotBlank(message = "probeId不能为空")
    @Size(max = 64, message = "probeId长度不能超过64")
    private String probeId;

    /** 评测活动标识。 */
    @NotBlank(message = "activityId不能为空")
    @Size(max = 64, message = "activityId长度不能超过64")
    private String activityId;

    /** 评测任务标识。 */
    @NotBlank(message = "taskId不能为空")
    @Size(max = 64, message = "taskId长度不能超过64")
    private String taskId;

    /** 本次运行标识。 */
    @NotBlank(message = "runId不能为空")
    @Size(max = 64, message = "runId长度不能超过64")
    private String runId;

    /** 当前风险和策略下的一次攻击尝试标识。 */
    @NotBlank(message = "attemptId不能为空")
    @Size(max = 64, message = "attemptId长度不能超过64")
    private String attemptId;

    /** 已由上层审批或预授权策略生成的运行授权引用。 */
    @NotBlank(message = "authorizationRef不能为空")
    @Size(max = 128, message = "authorizationRef长度不能超过128")
    private String authorizationRef;

    /** 现有 model_info 表中的被测模型 ID。 */
    @NotNull(message = "targetModelId不能为空")
    @Positive(message = "targetModelId必须大于0")
    private Long targetModelId;

    /** 本次探测希望覆盖的风险编码。 */
    @NotBlank(message = "riskId不能为空")
    @Size(max = 128, message = "riskId长度不能超过128")
    private String riskId;

    /** 后续判定必须使用的规则集版本。 */
    @NotBlank(message = "ruleSetVersion不能为空")
    @Size(max = 128, message = "ruleSetVersion长度不能超过128")
    private String ruleSetVersion;

    /** 目标交互模式；D01 只支持 STATIC_SINGLE_TURN。 */
    @NotNull(message = "executionMode不能为空")
    private ExecutionMode executionMode;

    /** 实际提交给被测目标的文本，原文只写入受限证据表。 */
    @NotBlank(message = "input不能为空")
    @Size(max = 100000, message = "input长度不能超过100000")
    private String input;

    /**
     * 本次业务额外要求的目标能力。例如要求 TOOL_TRACE 时，普通模型目标会被
     * 明确拒绝，不能降级成只看文本回复。
     */
    private Set<TargetCapability> requiredCapabilities;

    /**
     * 最晚开始目标调用的时间，使用 ISO-8601 UTC 时间；为空时采用客户端自身
     * 的超时配置。该字段不承诺强行终止已经发出的远程请求。
     */
    private Instant deadlineAt;
}
