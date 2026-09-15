package com.kant.agent.orchestration;

import com.kant.agent.agent.JudgeAgent;
import com.kant.agent.agent.SampleAgent;
import com.kant.agent.contract.AgentEvent;
import com.kant.agent.contract.AttackSample;
import com.kant.agent.contract.EvalTaskStatus;
import com.kant.agent.contract.JudgeInput;
import com.kant.agent.contract.RedTeamEvaluateRequest;
import com.kant.agent.contract.RedTeamTaskResult;
import com.kant.agent.contract.TargetModelRequest;
import com.kant.agent.contract.TargetModelResponse;
import com.kant.agent.contract.JudgeVerdict;
import com.kant.agent.gateway.TargetModelGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 第一阶段的确定性红队评测编排器。
 *
 * <p>编排器按照“校验请求 → 创建样本 → 调用目标模型 → Judge 判定 → 生成结果”的
 * 固定顺序驱动多个业务组件，并把每个关键节点记录为 {@link AgentEvent}。当前实现是
 * 同步单轮流程，后续异步化时可以沿用相同的状态和事件契约。</p>
 */
@Service
public class RedTeamOrchestrator {

    /** 负责把评测请求转换为可执行攻击样本的智能体。 */
    private final SampleAgent sampleAgent;

    /** 负责连接目标模型并返回统一调用结果的业务网关。 */
    private final TargetModelGateway targetModelGateway;

    /** 负责分析目标模型响应并生成安全结论的 Judge 智能体。 */
    private final JudgeAgent judgeAgent;

    /**
     * 创建红队评测编排器。
     *
     * @param sampleAgent 样本智能体
     * @param targetModelGateway 目标模型网关
     * @param judgeAgent Judge 智能体
     */
    public RedTeamOrchestrator(SampleAgent sampleAgent,
                               TargetModelGateway targetModelGateway,
                               JudgeAgent judgeAgent) {
        this.sampleAgent = sampleAgent;
        this.targetModelGateway = targetModelGateway;
        this.judgeAgent = judgeAgent;
    }

    /**
     * 同步执行一次单轮红队评测。
     *
     * <p>方法会为每次调用生成独立任务标识，并在异常发生时返回 FAILED 结果，而不是把
     * 已经产生的样本或模型响应丢失。目标模型调用失败时会提前结束，不会继续调用 Judge。</p>
     *
     * @param request 创建评测任务所需的业务请求
     * @return 包含最终状态、过程事件和阶段性产物的任务结果
     */
    public RedTeamTaskResult execute(RedTeamEvaluateRequest request) {
        // 任务 ID 在流程最开始生成，后续样本、事件和结果都通过它关联起来。
        String taskId = "RT-" + UUID.randomUUID().toString().substring(0, 8);
        List<AgentEvent> events = new ArrayList<>();

        // 这些变量用于在中途失败时保留已经完成的阶段产物。
        AttackSample sample = null;
        TargetModelResponse targetResponse = null;
        JudgeVerdict verdict = null;

        try {
            // 先记录 CREATED，再记录 VALIDATING，让前端能够还原任务从创建到校验的过程。
            addEvent(events, taskId, "TASK_CREATED", EvalTaskStatus.CREATED,
                    "红队评测任务已创建", Map.of());
            addEvent(events, taskId, "TASK_VALIDATING", EvalTaskStatus.VALIDATING,
                    "正在校验评测参数", Map.of());
            request.validate();

            // 参数通过后进入运行阶段，首先由样本智能体生成本轮要发送的样本。
            addEvent(events, taskId, "TASK_RUNNING", EvalTaskStatus.RUNNING,
                    "开始执行单轮评测", Map.of("riskCategory", request.riskCategory()));
            sample = sampleAgent.create(taskId, request);
            addEvent(events, taskId, "ATTACK_SAMPLE_CREATED", EvalTaskStatus.RUNNING,
                    "攻击样本已创建", Map.of("sampleId", sample.sampleId(), "fingerprint", sample.fingerprint()));

            // 将请求中的连接信息与样本正文组装成网关契约，编排器不直接操作厂商 SDK。
            targetResponse = targetModelGateway.call(new TargetModelRequest(
                    taskId,
                    sample.sampleId(),
                    request.targetModel(),
                    request.actualManufacturer(),
                    request.targetBaseUrl(),
                    request.targetApiKey(),
                    request.actualTargetModelId(),
                    sample.content(),
                    request.mockTargetResponse()
            ));
            // 无论成功或失败都记录一次模型调用事件，便于统计耗时和定位故障阶段。
            addEvent(events, taskId, "TARGET_MODEL_CALLED", EvalTaskStatus.RUNNING,
                    targetResponse.status() == com.kant.agent.contract.ModelCallStatus.SUCCESS
                            ? "目标模型调用完成" : "目标模型调用失败",
                    Map.of("status", targetResponse.status(), "elapsedMillis", targetResponse.elapsedMillis()));

            if (targetResponse.status() != com.kant.agent.contract.ModelCallStatus.SUCCESS) {
                // 模型没有给出可靠响应时不进入 Judge，避免把基础设施故障误判为安全结论。
                addEvent(events, taskId, "TASK_FAILED", EvalTaskStatus.FAILED,
                        "目标模型调用失败，任务结束", Map.of("errorType", targetResponse.errorType()));
                return new RedTeamTaskResult(taskId, EvalTaskStatus.FAILED, sample, targetResponse,
                        null, events, targetResponse.errorMessage());
            }

            // 只有模型调用成功后才进入判定阶段，Judge 只接收事实输入。
            addEvent(events, taskId, "JUDGING", EvalTaskStatus.JUDGING,
                    "Judge 正在分析目标模型响应", Map.of());
            verdict = judgeAgent.judge(new JudgeInput(
                    sample.content(),
                    targetResponse.content(),
                    sample.riskCategory(),
                    request.actualRuleVersion()
            ));
            // 保存 Judge 的摘要信息到事件中，完整结论仍然保存在任务结果的 verdict 字段。
            addEvent(events, taskId, "JUDGE_VERDICT_READY", EvalTaskStatus.JUDGING,
                    "Judge 判定已生成", Map.of("verdict", verdict.verdict(), "confidence", verdict.confidence()));
            // 最后记录完成事件，并返回包含全部阶段产物的同步结果。
            addEvent(events, taskId, "TASK_COMPLETED", EvalTaskStatus.COMPLETED,
                    "单轮红队评测已完成", Map.of());
            return new RedTeamTaskResult(taskId, EvalTaskStatus.COMPLETED, sample, targetResponse,
                    verdict, events, null);
        } catch (Exception e) {
            // 任意阶段出现未预期异常，都统一收敛为 FAILED，同时保留已记录的事件和产物。
            addEvent(events, taskId, "TASK_FAILED", EvalTaskStatus.FAILED,
                    "红队评测执行失败", Map.of("error", String.valueOf(e.getMessage())));
            return new RedTeamTaskResult(taskId, EvalTaskStatus.FAILED, sample, targetResponse,
                    verdict, events, e.getMessage());
        }
    }

    /**
     * 追加一条不可变的执行事件。
     *
     * @param events 当前任务的事件列表
     * @param taskId 所属评测任务标识
     * @param type 事件类型
     * @param status 事件对应的任务状态
     * @param message 事件说明
     * @param payload 事件附加数据
     */
    private void addEvent(List<AgentEvent> events, String taskId, String type,
                          EvalTaskStatus status, String message, Map<String, Object> payload) {
        // 事件 ID 独立于任务 ID，便于后续实时推送、日志关联和审计追踪。
        events.add(new AgentEvent(
                "evt-" + UUID.randomUUID().toString().substring(0, 8),
                taskId,
                type,
                status,
                message,
                payload,
                Instant.now()
        ));
    }
}
