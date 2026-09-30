package com.kant.agent.service.redteam;

import com.kant.agent.contract.budget.BudgetLedger;
import com.kant.agent.contract.budget.BudgetSnapshot;
import com.kant.agent.contract.common.ExecutionContext;
import com.kant.agent.contract.evidence.EvidenceContent;
import com.kant.agent.contract.evidence.EvidenceQuery;
import com.kant.agent.contract.evidence.EvidenceReference;
import com.kant.agent.contract.evidence.EvidenceType;
import com.kant.agent.contract.probe.ExecutionMode;
import com.kant.agent.contract.probe.ProbeCommand;
import com.kant.agent.contract.probe.ProbeExecutionStatus;
import com.kant.agent.contract.probe.ProbeLookup;
import com.kant.agent.contract.probe.ProbeSubmission;
import com.kant.agent.contract.probe.TargetObservation;
import com.kant.agent.contract.redteam.AdaptiveAttackContext;
import com.kant.agent.contract.redteam.AttackAction;
import com.kant.agent.contract.redteam.AttackActionType;
import com.kant.agent.contract.redteam.AttackDecision;
import com.kant.agent.contract.redteam.AttackDecisionStatus;
import com.kant.agent.contract.redteam.AttackHistoryItem;
import com.kant.agent.contract.redteam.AttackStopReason;
import com.kant.agent.contract.redteam.RedTeamActionRecord;
import com.kant.agent.contract.redteam.RedTeamAttempt;
import com.kant.agent.contract.redteam.RedTeamRun;
import com.kant.agent.contract.redteam.RedTeamRunStatus;
import com.kant.agent.contract.redteam.RedTeamTask;
import com.kant.agent.contract.redteam.RedTeamTaskStatus;
import com.kant.agent.contract.redteam.SuspectedFinding;
import com.kant.agent.contract.target.TargetCapability;
import com.kant.agent.port.evidence.EvidenceReadPort;
import com.kant.agent.port.probe.ProbeExecutionPort;
import com.kant.agent.port.redteam.AdaptiveAttackAgent;
import com.kant.agent.port.redteam.RedTeamRunRecorder;
import com.kant.agent.port.redteam.RedTeamRunStarter;
import com.kant.agent.port.redteam.RedTeamTaskRepository;
import com.kant.agent.service.budget.TokenEstimator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * D02 自动红队确定性编排器。
 *
 * <p>编排器负责状态、预算、动作校验和目标执行顺序。攻击 Agent 只负责提出
 * 下一步动作，任何动作都必须先经过本类和 {@link AttackActionValidator}，
 * 再进入 D01 的 {@code ProbeExecutionPort}。</p>
 *
 * <p>D02 仍然以同步方式推进一条单风险、单尝试链路。运行事实已经逐步持久化，
 * 后续改成后台 Worker 时不需要改变动作和探测契约。</p>
 */
public class EvaluationOrchestrator {

    /** D01 证据表中表示物理探测执行的固定所属类型。 */
    private static final String PROBE_EXECUTION_OWNER_TYPE = "PROBE_EXECUTION";

    private final RedTeamTaskRepository taskRepository;
    private final RedTeamRunRecorder runRecorder;
    private final RedTeamRunStarter runStarter;
    private final AdaptiveAttackAgent attackAgent;
    private final AttackActionValidator actionValidator;
    private final ProbeExecutionPort probeExecutionPort;
    private final EvidenceReadPort evidenceReadPort;

    public EvaluationOrchestrator(RedTeamTaskRepository taskRepository,
                                  RedTeamRunRecorder runRecorder,
                                  RedTeamRunStarter runStarter,
                                  AdaptiveAttackAgent attackAgent,
                                  AttackActionValidator actionValidator,
                                  ProbeExecutionPort probeExecutionPort,
                                  EvidenceReadPort evidenceReadPort) {
        this.taskRepository = taskRepository;
        this.runRecorder = runRecorder;
        this.runStarter = runStarter;
        this.attackAgent = attackAgent;
        this.actionValidator = actionValidator;
        this.probeExecutionPort = probeExecutionPort;
        this.evidenceReadPort = evidenceReadPort;
    }

    /**
     * 启动一条 D02 运行并返回最终或当前可见的运行摘要。
     *
     * @param tenantId 租户或业务空间
     * @param actorId  当前操作者
     * @param taskId   已创建的红队任务
     * @param runId    本次运行标识
     * @return 运行结束摘要
     */
    public RedTeamRun start(String tenantId, String actorId, String taskId, String runId) {
        RedTeamTask task = taskRepository.find(tenantId, actorId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("自动红队任务不存在，taskId=" + taskId));
        if (task.status() == RedTeamTaskStatus.RUNNING) {
            throw new RedTeamRunStateConflictException(
                    "自动红队任务已经有运行正在执行，taskId=" + taskId);
        }
        if (task.status() == RedTeamTaskStatus.BLOCKED) {
            // D02 尚未提供未知结果核对和恢复入口，禁止直接重新发起目标调用，
            // 避免把可能已经执行的请求无条件重复发送。
            throw new RedTeamRunStateConflictException(
                    "自动红队任务处于BLOCKED状态，需先核对目标结果，taskId=" + taskId);
        }

        Instant createdAt = Instant.now();
        Instant startedAt = createdAt;
        String attemptId = newId("attempt_");
        RedTeamTask runningTask = copyTask(task, RedTeamTaskStatus.RUNNING, null, null, startedAt, null);
        // 运行尚未对外可见时就完成任务抢占和运行创建，避免两个并发请求都
        // 通过“读取到READY后再更新”的检查并同时触达被测目标。
        RedTeamRun run = new RedTeamRun(
                runId,
                task.taskId(),
                tenantId,
                actorId,
                RedTeamRunStatus.RUNNING,
                new BudgetSnapshot(0, 0, 0, 0, 0),
                0,
                attemptId,
                null,
                null,
                false,
                createdAt,
                startedAt,
                null);
        if (!runStarter.tryCreateAndClaim(task, runningTask, run)) {
            throw new RedTeamRunStateConflictException(
                    "自动红队任务启动竞争失败，任务状态已被其他请求改变，taskId=" + taskId);
        }

        BudgetLedger ledger = new BudgetLedger(task.budgetPolicy());
        if (!ledger.reserveAttempt()) {
            return finish(task, run, ledger, attemptId, 0, 0,
                    RedTeamRunStatus.COMPLETED,
                    AttackStopReason.BUDGET_EXHAUSTED,
                    "攻击尝试预算为零，未调用目标");
        }

        Instant attemptStartedAt = Instant.now();
        RedTeamAttempt attempt = new RedTeamAttempt(
                attemptId,
                task.taskId(),
                runId,
                1,
                task.riskId(),
                task.strategyVersion(),
                RedTeamRunStatus.RUNNING,
                0,
                null,
                null,
                attemptStartedAt,
                null);
        runRecorder.saveAttempt(attempt);

        // 尝试事实创建成功后立即同步运行检查点，避免进程在首个动作前中断时，
        // 运行摘要仍显示attempts=0而实际已经预留了一个攻击尝试。
        run = copyRun(run,
                RedTeamRunStatus.RUNNING,
                ledger.snapshot(),
                0,
                attemptId,
                null,
                null,
                runRecorder.isStopRequested(tenantId, actorId, runId),
                startedAt,
                null);
        runRecorder.updateRun(run);

        List<AttackHistoryItem> history = new ArrayList<>();
        int noProgressCount = 0;
        int actionCount = 0;
        int probeCount = 0;
        String previousResponseHash = null;

        while (true) {
            if (runRecorder.isStopRequested(tenantId, actorId, runId)) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.STOPPED,
                        AttackStopReason.USER_STOPPED,
                        "用户已请求停止运行");
            }
            if (durationExceeded(startedAt, task.budgetPolicy().maxDurationSeconds())) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.COMPLETED,
                        AttackStopReason.TIME_LIMIT_REACHED,
                        "运行达到任务配置的最长时间");
            }

            BudgetSnapshot budget = ledger.snapshot();
            AdaptiveAttackContext context = new AdaptiveAttackContext(
                    task.taskId(),
                    runId,
                    attemptId,
                    task.targetSnapshotRef(),
                    task.riskId(),
                    task.strategyVersion(),
                    task.seedInput(),
                    history,
                    Math.max(0, task.budgetPolicy().maxTargetCalls() - budget.targetCalls()),
                    Math.max(0, task.budgetPolicy().maxAgentCalls() - budget.agentCalls()),
                    noProgressCount,
                    false);

            long plannedAgentCalls;
            try {
                plannedAgentCalls = attackAgent.plannedAgentCalls(context);
                if (plannedAgentCalls < 0) {
                    throw new IllegalArgumentException("攻击 Agent 预估调用次数不能小于0");
                }
            } catch (RuntimeException ex) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.FAILED,
                        AttackStopReason.GENERATION_FAILED,
                        "攻击 Agent 预算预估失败：" + safeMessage(ex));
            }
            if (!ledger.reserveAgentCalls(plannedAgentCalls)) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.COMPLETED,
                        AttackStopReason.BUDGET_EXHAUSTED,
                        "Agent调用预算已用尽，未调用攻击 Agent");
            }

            // Agent调用预算在真正调用攻击 Agent 前先写入运行检查点。这样即使
            // 进程在模型调用期间中断，恢复程序也不会把已经预留的Agent额度误判为未使用。
            boolean stopRequestedBeforeAgent = runRecorder.isStopRequested(
                    tenantId, actorId, runId);
            run = copyRun(run,
                    RedTeamRunStatus.RUNNING,
                    ledger.snapshot(),
                    actionCount,
                    attemptId,
                    null,
                    null,
                    stopRequestedBeforeAgent,
                    run.startedAt(),
                    null);
            runRecorder.updateRun(run);
            if (runRecorder.isStopRequested(tenantId, actorId, runId)) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.STOPPED,
                        AttackStopReason.USER_STOPPED,
                        "用户在攻击 Agent 调用前请求停止运行");
            }
            if (durationExceeded(startedAt, task.budgetPolicy().maxDurationSeconds())) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.COMPLETED,
                        AttackStopReason.TIME_LIMIT_REACHED,
                        "运行在攻击 Agent 调用前达到最长时间");
            }

            AttackDecision decision;
            try {
                decision = attackAgent.decide(context);
            } catch (RuntimeException ex) {
                // 攻击模型自身失败不能被记录为被测目标拒答；这里直接结束为生成失败，
                // 同时保留已经完成的目标探测和预算事实。
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.FAILED,
                        AttackStopReason.GENERATION_FAILED,
                        "攻击 Agent 调用异常：" + safeMessage(ex));
            }
            if (decision == null) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.FAILED,
                        AttackStopReason.GENERATION_FAILED,
                        "攻击 Agent 返回了空决策");
            }
            if (decision.agentCalls() > plannedAgentCalls) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.FAILED,
                        AttackStopReason.GENERATION_FAILED,
                        "攻击 Agent 实际调用次数超过调用前声明的预留上限");
            }
            if (decision.status() == AttackDecisionStatus.GENERATION_FAILED) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.FAILED,
                        AttackStopReason.GENERATION_FAILED,
                        safeDecisionMessage(decision));
            }

            AttackAction action = decision.action();

            // 停止意图优先于 Agent 输出校验。即使模型在停止请求到达时返回了
            // 一个引用过期的动作，也不能让模型输出把运行状态改写成失败。
            if (runRecorder.isStopRequested(tenantId, actorId, runId)) {
                int stoppedSequence = ++actionCount;
                saveStoppedAction(task, runId, attemptId, stoppedSequence, action,
                        action.input() == null ? null : sha256(action.input()),
                        newId("action_"),
                        AttackStopReason.USER_STOPPED,
                        "USER_STOPPED");
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.STOPPED,
                        AttackStopReason.USER_STOPPED,
                        "用户在动作执行前请求停止运行");
            }

            // Agent 生成动作本身可能耗时。期限检查同样优先于动作校验和目标
            // 调用，避免已过期的模型建议改变运行的最终停止原因。
            if (durationExceeded(startedAt, task.budgetPolicy().maxDurationSeconds())) {
                int stoppedSequence = ++actionCount;
                saveStoppedAction(task, runId, attemptId, stoppedSequence, action,
                        action.input() == null ? null : sha256(action.input()),
                        newId("action_"),
                        AttackStopReason.TIME_LIMIT_REACHED,
                        "TIME_LIMIT_REACHED");
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.COMPLETED,
                        AttackStopReason.TIME_LIMIT_REACHED,
                        "运行在动作校验前达到最长时间");
            }

            try {
                actionValidator.validate(action, context);
            } catch (RuntimeException ex) {
                saveRejectedAction(task, runId, attemptId, ++actionCount, action);
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.FAILED,
                        AttackStopReason.ACTION_REJECTED,
                        safeMessage(ex));
            }

            if (action.actionType() == AttackActionType.END_ATTEMPT) {
                saveEndedAction(task, runId, attemptId, ++actionCount, action);
                AttackStopReason stopReason = action.suggestedStopReason() == null
                        ? AttackStopReason.STRATEGY_EXHAUSTED
                        : action.suggestedStopReason();
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.COMPLETED,
                        stopReason,
                        action.reasonSummary());
            }

            // 输入 Token 在目标调用前先预留。这样即使目标响应尚未返回，
            // 运行记录也不会在预算边界上继续安排新的目标请求。
            long reservedInputTokens = TokenEstimator.estimate(action.input());
            if (!ledger.reserveInputTokens(reservedInputTokens)) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.COMPLETED,
                        AttackStopReason.BUDGET_EXHAUSTED,
                        "Token预算不足，未调用目标");
            }
            if (!ledger.reserveTargetCall()) {
                // 目标调用额度不足时，本次动作没有触达目标，释放刚才的Token预留。
                ledger.releaseReservedInputTokens(reservedInputTokens);
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.COMPLETED,
                        AttackStopReason.BUDGET_EXHAUSTED,
                        "目标调用预算已用尽");
            }

            int sequence = ++actionCount;
            String actionId = newId("action_");
            String inputHash = sha256(action.input());
            saveActionIntent(task, runId, attemptId, sequence, actionId, action, inputHash);

            // 先把预算和动作意图写入运行摘要，再进入外部目标调用边界。
            // 进程在目标调用期间中断时，恢复程序至少可以看到一条待执行动作，
            // 不会把已经预留的预算误显示为零。
            run = copyRun(run,
                    RedTeamRunStatus.RUNNING,
                    ledger.snapshot(),
                    actionCount,
                    attemptId,
                    null,
                    null,
                    runRecorder.isStopRequested(tenantId, actorId, runId),
                    run.startedAt(),
                    null);
            runRecorder.updateRun(run);
            if (runRecorder.isStopRequested(tenantId, actorId, runId)) {
                // 目标调用尚未发出，释放输入Token在途预留。目标调用次数仍保留为
                // 已预留额度，便于审计本次动作曾经通过预算闸门但最终未触达目标。
                ledger.releaseReservedInputTokens(reservedInputTokens);
                saveStoppedAction(task, runId, attemptId, sequence, action, inputHash, actionId,
                        AttackStopReason.USER_STOPPED, "USER_STOPPED");
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.STOPPED,
                        AttackStopReason.USER_STOPPED,
                        "用户在目标调用开始前请求停止运行");
            }
            if (durationExceeded(startedAt, task.budgetPolicy().maxDurationSeconds())) {
                // 目标调用尚未发出，输入Token预留可以释放；目标调用额度仍保留，
                // 以记录本动作已经通过预算闸门但因期限没有触达目标。
                ledger.releaseReservedInputTokens(reservedInputTokens);
                saveStoppedAction(task, runId, attemptId, sequence, action, inputHash, actionId,
                        AttackStopReason.TIME_LIMIT_REACHED, "TIME_LIMIT_REACHED");
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.COMPLETED,
                        AttackStopReason.TIME_LIMIT_REACHED,
                        "运行在目标调用开始前达到最长时间");
            }

            TargetObservation observation;
            ProbeSubmission submission = null;
            try {
                String probeId = deterministicProbeId(runId, sequence);
                String probeIdempotencyKey = runId + ":d02:probe:" + sequence;
                ProbeCommand probeCommand = new ProbeCommand(
                        probeIdempotencyKey,
                        new ExecutionContext(
                                tenantId,
                                actorId,
                                task.activityId(),
                                task.taskId(),
                                runId,
                                attemptId,
                                newId("trace_"),
                                task.authorizationRef()),
                         probeId,
                         task.target(),
                         task.targetSnapshotRef(),
                         task.riskId(),
                        task.ruleSetVersion(),
                        // 自适应发生在攻击者历史层；每次目标调用本身仍是干净单轮上下文。
                        ExecutionMode.STATIC_SINGLE_TURN,
                        null,
                        action.input(),
                        Set.of(TargetCapability.SINGLE_TURN),
                        null);
                submission = probeExecutionPort.submit(probeCommand);
                if (submission == null) {
                    throw new IllegalStateException("探测执行端口返回了空的提交结果");
                }
                // submit 已经返回，说明 D01 至少完成了逻辑探测的受理或幂等
                // 命中。即使后续读取观察失败，也要把这次探测计入尝试事实。
                probeCount++;
                // 复制为局部不可变引用，既便于后续查询，也避免在异常分支中
                // 使用可变的提交变量作为Lambda捕获值。
                ProbeSubmission acceptedSubmission = submission;
                observation = probeExecutionPort.find(new ProbeLookup(
                                tenantId,
                                actorId,
                                acceptedSubmission.reference().executionId()))
                        .orElseThrow(() -> new IllegalStateException(
                                "探测已受理但无法读取执行观察，executionId="
                                        + acceptedSubmission.reference().executionId()));
                validateObservation(task, probeCommand, submission, observation);
            } catch (Exception ex) {
                // 端口异常可能发生在受理前，也可能发生在目标已经被调用后。
                // 当前没有统一的远端状态查询能力，只能保守保留已预留Token并阻断运行，
                // 不能把这类异常直接当成一次明确失败后继续攻击。
                ledger.settleUnknownTargetTokens(reservedInputTokens);
                saveFailedAction(task, runId, attemptId, sequence, actionId, action,
                        inputHash, submission);
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.BLOCKED,
                        AttackStopReason.TARGET_RESULT_UNKNOWN,
                        "提交目标探测失败且无法确认目标调用结果：" + safeMessage(ex));
            }

            if (!observation.status().isTerminal()) {
                // D01 允许返回尚未终态的观察。此时目标是否已经开始、是否会
                // 产生响应都还不能确认，不能释放预算或继续发送下一条输入。
                ledger.settleUnknownTargetCalls(1L, observation.usage().targetCalls());
                settleObservationTokensConservatively(ledger, reservedInputTokens, observation);
                savePendingAction(task, runId, attemptId, sequence, actionId, action,
                        inputHash, observation);
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.BLOCKED,
                        AttackStopReason.TARGET_RESULT_UNKNOWN,
                        "目标探测尚未进入终态，等待执行状态核对");
            }

            boolean targetBudgetExceeded = false;
            boolean tokenUsageUnknown = false;
            boolean tokenBudgetExceeded = false;

            // 成功状态和零目标调用互相矛盾。即使返回对象来自第三方端口而没有
            // 经过领域构造器，也不能把这种不完整账目当成一次安全观察继续推进。
            if (observation.status() == ProbeExecutionStatus.SUCCEEDED
                    && observation.usage().targetCalls() == 0) {
                ledger.settleUnknownTargetCalls(1L, 0L);
                settleObservationTokensConservatively(ledger, reservedInputTokens, observation);
                completeAction(task, runId, attemptId, sequence, actionId, action,
                        inputHash, observation, false);
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.BLOCKED,
                        AttackStopReason.BUDGET_USAGE_UNKNOWN,
                        "目标返回SUCCEEDED但targetCalls为0，等待执行账目核对");
            }

            if (observation.status() == ProbeExecutionStatus.UNKNOWN) {
                // UNKNOWN 不能释放已经预留的目标调用额度；如果执行层已知发生
                // 多次物理调用，把超出预留的部分追加到账本后再阻断运行。
                targetBudgetExceeded = !ledger.settleUnknownTargetCalls(
                        1L, observation.usage().targetCalls());
                if (observation.usage().inputTokens() != null
                        || observation.usage().outputTokens() != null) {
                    // 结果虽然未知，但执行层可能已经返回部分用量。已知的
                    // 用量仍然应进入账本，未知部分通过BLOCKED状态隔离。
                    tokenBudgetExceeded = !ledger.settlePartiallyKnownTargetTokens(
                            reservedInputTokens,
                            observation.usage().inputTokens(),
                            observation.usage().outputTokens());
                } else {
                    ledger.settleUnknownTargetTokens(reservedInputTokens);
                    tokenUsageUnknown = true;
                }
            } else if ((observation.status() == ProbeExecutionStatus.FAILED
                    || observation.status() == ProbeExecutionStatus.CANCELLED)
                    && observation.usage().targetCalls() == 0) {
                // D01 已确认目标调用没有开始，已预留的目标额度可以释放；
                // 只有在没有任何异常用量字段时，输入Token预留才可以释放。
                targetBudgetExceeded = !ledger.settleKnownTargetCalls(1L, 0L);
                if (observation.usage().inputTokens() != null
                        || observation.usage().outputTokens() != null) {
                    // 目标调用次数为零但观察携带了部分用量，说明执行层的
                    // 计量事实不完整；已知部分仍入账，运行随后进入BLOCKED。
                    tokenBudgetExceeded = !ledger.settlePartiallyKnownTargetTokens(
                            reservedInputTokens,
                            observation.usage().inputTokens(),
                            observation.usage().outputTokens());
                    tokenUsageUnknown = true;
                } else {
                    ledger.releaseReservedInputTokens(reservedInputTokens);
                }
            } else {
                // 终态结果带有明确的目标调用计量，用实际物理调用数替换本次预留。
                targetBudgetExceeded = !ledger.settleKnownTargetCalls(
                        1L, observation.usage().targetCalls());
                if (observation.usage().inputTokens() != null
                        && observation.usage().outputTokens() != null
                        && observation.usage().measurement()
                        != com.kant.agent.contract.probe.UsageMeasurement.UNAVAILABLE) {
                    tokenBudgetExceeded = !ledger.settleKnownTargetTokens(
                            reservedInputTokens,
                            observation.usage().inputTokens(),
                            observation.usage().outputTokens());
                } else if (observation.usage().inputTokens() != null
                        || observation.usage().outputTokens() != null) {
                    tokenBudgetExceeded = !ledger.settlePartiallyKnownTargetTokens(
                            reservedInputTokens,
                            observation.usage().inputTokens(),
                            observation.usage().outputTokens());
                    tokenUsageUnknown = true;
                } else {
                    // 目标已经调用但无法结算完整用量，保守记账并阻断后续动作。
                    ledger.settleUnknownTargetTokens(reservedInputTokens);
                    tokenUsageUnknown = true;
                }
            }

            EvidenceContent responseEvidence;
            try {
                responseEvidence = readResponseEvidence(tenantId, actorId, observation);
                validateResponseEvidence(observation, responseEvidence);
            } catch (RuntimeException ex) {
                // 用量已经完成结算，证据读取失败仍然不能被解释成目标安全。
                completeAction(task, runId, attemptId, sequence, actionId, action,
                        inputHash, observation, false);
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.BLOCKED,
                        AttackStopReason.TARGET_RESULT_UNKNOWN,
                        "目标执行事实已保存，但响应证据读取失败：" + safeMessage(ex));
            }
            String response = responseEvidence == null ? null : responseEvidence.content();
            boolean riskObserved = response != null && containsRiskSignal(response, task.riskIndicators());
            completeAction(task, runId, attemptId, sequence, actionId, action,
                    inputHash, observation, riskObserved);
            boolean stopRequestedAfterObservation = runRecorder.isStopRequested(
                    tenantId, actorId, runId);

            if (observation.status() == ProbeExecutionStatus.SUCCEEDED
                    && responseEvidence == null) {
                // D01 已确认目标返回成功，但响应证据正文无法读取；这不是目标失败，
                // 也不能当作安全结果，必须阻塞当前运行等待证据核对。
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.BLOCKED,
                        AttackStopReason.TARGET_RESULT_UNKNOWN,
                        "目标执行已成功，但响应证据缺失或无权读取");
            }

            if (observation.status() == ProbeExecutionStatus.UNKNOWN) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.BLOCKED,
                        AttackStopReason.TARGET_RESULT_UNKNOWN,
                        "目标调用结果未知，等待人工或目标状态核对");
            }
            if (tokenUsageUnknown) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.BLOCKED,
                        AttackStopReason.BUDGET_USAGE_UNKNOWN,
                        "目标调用已完成但Token用量无法完整结算，等待预算事实核对");
            }
            if (observation.status() != ProbeExecutionStatus.SUCCEEDED || response == null) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.FAILED,
                        AttackStopReason.TARGET_EXECUTION_FAILED,
                        "目标响应或响应证据不可用，不能形成无风险结论");
            }

            if (riskObserved) {
                SuspectedFinding finding = new SuspectedFinding(
                        deterministicFindingId(runId, actionId),
                        task.taskId(),
                        runId,
                        attemptId,
                        actionId,
                        task.riskId(),
                        evidenceRefs(observation),
                        "目标响应命中任务配置的疑似风险信号，尚未经过独立复核",
                        "SUSPECTED",
                        Instant.now());
                runRecorder.saveFinding(finding);
                if (stopRequestedAfterObservation) {
                    return finish(task, run, ledger, attemptId, actionCount, probeCount,
                            RedTeamRunStatus.STOPPED,
                            AttackStopReason.USER_STOPPED,
                            "用户在目标调用完成后请求停止运行；已保留本次疑似发现");
                }
                if (durationExceeded(startedAt, task.budgetPolicy().maxDurationSeconds())) {
                    return finish(task, run, ledger, attemptId, actionCount, probeCount,
                            RedTeamRunStatus.COMPLETED,
                            AttackStopReason.TIME_LIMIT_REACHED,
                            "目标响应已保存并产生疑似发现，但运行已达到最长时间");
                }
                if (targetBudgetExceeded) {
                    return finish(task, run, ledger, attemptId, actionCount, probeCount,
                            RedTeamRunStatus.COMPLETED,
                            AttackStopReason.BUDGET_EXHAUSTED,
                            "已记录疑似发现，但实际目标调用次数已达到预算上限");
                }
                if (tokenBudgetExceeded) {
                    return finish(task, run, ledger, attemptId, actionCount, probeCount,
                            RedTeamRunStatus.COMPLETED,
                            AttackStopReason.BUDGET_EXHAUSTED,
                            "已记录疑似发现，但估算Token消耗已达到预算上限");
                }
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.COMPLETED,
                        AttackStopReason.RISK_OBSERVED,
                        "已产生疑似发现，等待独立复核和复现");
            }

            if (stopRequestedAfterObservation) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.STOPPED,
                        AttackStopReason.USER_STOPPED,
                        "用户在目标调用完成后请求停止运行");
            }

            if (durationExceeded(startedAt, task.budgetPolicy().maxDurationSeconds())) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.COMPLETED,
                        AttackStopReason.TIME_LIMIT_REACHED,
                        "目标响应已保存，但运行达到最长时间");
            }

            if (targetBudgetExceeded) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.COMPLETED,
                        AttackStopReason.BUDGET_EXHAUSTED,
                        "实际目标调用次数已达到预算上限");
            }

            if (tokenBudgetExceeded) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.COMPLETED,
                        AttackStopReason.BUDGET_EXHAUSTED,
                        "估算Token消耗已达到预算上限");
            }

            if (response.length() > AttackHistoryItem.MAX_RESPONSE_LENGTH) {
                // 目标原始响应已经由D01证据表完整保存，但过大的正文不能直接
                // 注入攻击上下文；保留证据并阻塞，避免截断后改变判定语义。
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.BLOCKED,
                        AttackStopReason.TARGET_RESULT_UNKNOWN,
                        "目标响应超过攻击上下文长度上限，需人工核对原始证据");
            }

            String responseHash = sha256(response);
            if (responseHash.equals(previousResponseHash)) {
                noProgressCount++;
            } else {
                noProgressCount = 0;
            }
            previousResponseHash = responseHash;
            history.add(new AttackHistoryItem(
                    history.size() + 1,
                    observation.reference().probeId(),
                    observation.reference().executionId(),
                    action.input(),
                    observation.status(),
                    response,
                    observation.responseEvidence() == null
                            ? null
                            : observation.responseEvidence().evidenceRef(),
                    observation.errorCode(),
                    observation.errorMessage()));

            attempt = new RedTeamAttempt(
                    attemptId,
                    task.taskId(),
                    runId,
                    1,
                    task.riskId(),
                    task.strategyVersion(),
                    RedTeamRunStatus.RUNNING,
                    probeCount,
                    null,
                    null,
                    attempt.startedAt(),
                    null);
            runRecorder.saveAttempt(attempt);
            run = copyRun(run,
                    RedTeamRunStatus.RUNNING,
                    ledger.snapshot(),
                    actionCount,
                    attemptId,
                    null,
                    null,
                    runRecorder.isStopRequested(tenantId, actorId, runId),
                    run.startedAt(),
                    null);
            runRecorder.updateRun(run);

            if (noProgressCount >= 2) {
                return finish(task, run, ledger, attemptId, actionCount, probeCount,
                        RedTeamRunStatus.COMPLETED,
                        AttackStopReason.NO_PROGRESS,
                        "连续多轮响应没有产生新的有效信息");
            }
        }
    }

    /** 读取 D01 响应证据；读不到正文时不把它当成安全结果。 */
    private EvidenceContent readResponseEvidence(String tenantId,
                                                 String actorId,
                                                 TargetObservation observation) {
        if (observation.responseEvidence() == null) {
            return null;
        }
        return evidenceReadPort.read(new EvidenceQuery(
                        tenantId,
                        actorId,
                        observation.reference().executionId(),
                        observation.responseEvidence().evidenceRef()))
                .orElse(null);
    }

    /** 校验证据读取端口返回的正文仍属于当前目标响应证据。 */
    private void validateResponseEvidence(TargetObservation observation,
                                          EvidenceContent evidenceContent) {
        if (evidenceContent == null || observation.responseEvidence() == null) {
            return;
        }
        if (!observation.responseEvidence().evidenceRef()
                .equals(evidenceContent.reference().evidenceRef())
                || evidenceContent.reference().type() != EvidenceType.TARGET_RESPONSE
                || !PROBE_EXECUTION_OWNER_TYPE.equals(
                evidenceContent.reference().ownerType())
                || !PROBE_EXECUTION_OWNER_TYPE.equals(
                observation.responseEvidence().ownerType())
                || !observation.reference().executionId()
                .equals(evidenceContent.reference().ownerRef())) {
            throw new IllegalStateException("响应证据引用与当前目标执行不一致");
        }
    }

    /** 使用字面风险信号做 D02 的保守疑似发现检测。 */
    private boolean containsRiskSignal(String response, List<String> indicators) {
        String normalized = response.toLowerCase(java.util.Locale.ROOT);
        return indicators.stream()
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    /**
     * 在观察尚未提供完整计量时保守结算 Token。
     *
     * <p>该方法只负责账本结算，不改变调用方随后选择的 BLOCKED 或失败状态；
     * 执行结果是否可继续推进仍由编排器根据观察状态决定。</p>
     */
    private void settleObservationTokensConservatively(BudgetLedger ledger,
                                                       long reservedInputTokens,
                                                       TargetObservation observation) {
        if (observation.usage().inputTokens() != null
                || observation.usage().outputTokens() != null) {
            ledger.settlePartiallyKnownTargetTokens(
                    reservedInputTokens,
                    observation.usage().inputTokens(),
                    observation.usage().outputTokens());
        } else {
            ledger.settleUnknownTargetTokens(reservedInputTokens);
        }
    }

    /** 生成发现所需的输入、响应和错误证据引用。 */
    private List<String> evidenceRefs(TargetObservation observation) {
        List<String> refs = new ArrayList<>();
        if (observation.inputEvidence() != null) {
            refs.add(observation.inputEvidence().evidenceRef());
        }
        if (observation.responseEvidence() != null) {
            refs.add(observation.responseEvidence().evidenceRef());
        }
        if (observation.errorEvidence() != null) {
            refs.add(observation.errorEvidence().evidenceRef());
        }
        return refs;
    }

    private void saveActionIntent(RedTeamTask task,
                                  String runId,
                                  String attemptId,
                                  int sequence,
                                  String actionId,
                                  AttackAction action,
                                  String inputHash) {
        runRecorder.saveAction(new RedTeamActionRecord(
                actionId,
                task.taskId(),
                runId,
                attemptId,
                sequence,
                action.actionType(),
                action.strategyVersion(),
                inputHash,
                action.reasonSummary(),
                action.observationRefs(),
                action.suggestedStopReason(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                "PROPOSED",
                Instant.now()));
    }

    private void saveEndedAction(RedTeamTask task,
                                 String runId,
                                 String attemptId,
                                 int sequence,
                                 AttackAction action) {
        runRecorder.saveAction(new RedTeamActionRecord(
                newId("action_"),
                task.taskId(),
                runId,
                attemptId,
                sequence,
                action.actionType(),
                action.strategyVersion(),
                null,
                action.reasonSummary(),
                action.observationRefs(),
                action.suggestedStopReason(),
                action.suggestedStopReason(),
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                "ENDED",
                Instant.now()));
    }

    /**
     * 将已经持久化但尚未进入目标调用的动作标记为已停止调度。
     *
     * <p>用户停止或运行期限到达都可能发生在动作意图保存之后。保留同一个
     * actionId 更新记录，可以让恢复和审计明确知道该动作没有触达被测目标。</p>
     */
    private void saveStoppedAction(RedTeamTask task,
                                   String runId,
                                   String attemptId,
                                   int sequence,
                                   AttackAction action,
                                   String inputHash,
                                   String actionId,
                                   AttackStopReason actualStopReason,
                                   String errorCode) {
        runRecorder.saveAction(new RedTeamActionRecord(
                actionId,
                task.taskId(),
                runId,
                attemptId,
                sequence,
                action.actionType(),
                action.strategyVersion(),
                inputHash,
                action.reasonSummary(),
                action.observationRefs(),
                action.suggestedStopReason(),
                actualStopReason,
                null,
                null,
                null,
                null,
                null,
                errorCode,
                false,
                "STOPPED",
                Instant.now()));
    }

    private void saveRejectedAction(RedTeamTask task,
                                    String runId,
                                    String attemptId,
                                    int sequence,
                                    AttackAction action) {
        runRecorder.saveAction(new RedTeamActionRecord(
                newId("action_"),
                task.taskId(),
                runId,
                attemptId,
                sequence,
                action.actionType(),
                action.strategyVersion(),
                action.input() == null ? null : sha256(action.input()),
                action.reasonSummary(),
                action.observationRefs(),
                action.suggestedStopReason(),
                AttackStopReason.ACTION_REJECTED,
                null,
                null,
                null,
                null,
                null,
                "ACTION_REJECTED",
                false,
                "REJECTED",
                Instant.now()));
    }

    private void saveFailedAction(RedTeamTask task,
                                  String runId,
                                  String attemptId,
                                  int sequence,
                                  String actionId,
                                  AttackAction action,
                                  String inputHash,
                                  ProbeSubmission submission) {
        runRecorder.saveAction(new RedTeamActionRecord(
                actionId,
                task.taskId(),
                runId,
                attemptId,
                sequence,
                action.actionType(),
                action.strategyVersion(),
                inputHash,
                action.reasonSummary(),
                action.observationRefs(),
                action.suggestedStopReason(),
                AttackStopReason.TARGET_RESULT_UNKNOWN,
                null,
                submission == null ? null : submission.reference().probeId(),
                submission == null ? null : submission.reference().executionId(),
                submission == null ? null : submission.status(),
                null,
                "TARGET_RESULT_UNKNOWN",
                false,
                "EXECUTION_UNKNOWN",
                Instant.now()));
    }

    /** 保存仍在执行中的目标动作，避免把非终态观察误记成明确失败。 */
    private void savePendingAction(RedTeamTask task,
                                   String runId,
                                   String attemptId,
                                   int sequence,
                                   String actionId,
                                   AttackAction action,
                                   String inputHash,
                                   TargetObservation observation) {
        runRecorder.saveAction(new RedTeamActionRecord(
                actionId,
                task.taskId(),
                runId,
                attemptId,
                sequence,
                action.actionType(),
                action.strategyVersion(),
                inputHash,
                action.reasonSummary(),
                action.observationRefs(),
                action.suggestedStopReason(),
                AttackStopReason.TARGET_RESULT_UNKNOWN,
                observation.inputEvidence() == null
                        ? null
                        : observation.inputEvidence().evidenceRef(),
                observation.reference().probeId(),
                observation.reference().executionId(),
                observation.status(),
                observation.responseEvidence() == null
                        ? null
                        : observation.responseEvidence().evidenceRef(),
                "TARGET_EXECUTION_PENDING",
                false,
                "EXECUTION_UNKNOWN",
                Instant.now()));
    }

    private void completeAction(RedTeamTask task,
                                String runId,
                                String attemptId,
                                int sequence,
                                String actionId,
                                AttackAction action,
                                String inputHash,
                                TargetObservation observation,
                                boolean riskObserved) {
        runRecorder.saveAction(new RedTeamActionRecord(
                actionId,
                task.taskId(),
                runId,
                attemptId,
                sequence,
                action.actionType(),
                action.strategyVersion(),
                inputHash,
                action.reasonSummary(),
                action.observationRefs(),
                action.suggestedStopReason(),
                actualStopReason(observation),
                observation.inputEvidence() == null ? null : observation.inputEvidence().evidenceRef(),
                observation.reference().probeId(),
                observation.reference().executionId(),
                observation.status(),
                observation.responseEvidence() == null
                        ? null
                        : observation.responseEvidence().evidenceRef(),
                observation.errorCode(),
                riskObserved,
                actionStatus(observation),
                Instant.now()));
    }

    /** 校验 D01 返回的观察确实属于当前任务、运行和动作。 */
    private void validateObservation(RedTeamTask task,
                                     ProbeCommand command,
                                     ProbeSubmission submission,
                                     TargetObservation observation) {
        if (!submission.reference().probeId().equals(observation.reference().probeId())
                || !submission.reference().executionId().equals(observation.reference().executionId())
                || !command.probeId().equals(observation.reference().probeId())
                || !command.idempotencyKey().equals(observation.idempotencyKey())) {
            throw new IllegalStateException("探测提交引用与执行观察不一致");
        }
        if (!command.context().tenantId().equals(observation.context().tenantId())
                || !command.context().actorId().equals(observation.context().actorId())
                || !task.activityId().equals(observation.context().activityId())
                || !task.taskId().equals(observation.context().taskId())
                || !command.context().runId().equals(observation.context().runId())
                || !command.context().attemptId().equals(observation.context().attemptId())
                || !command.context().traceId().equals(observation.context().traceId())
                || !command.context().authorizationRef().equals(observation.context().authorizationRef())) {
            throw new IllegalStateException("执行观察不属于当前自动红队运行");
        }
        if (!command.target().equals(observation.target())
                || !command.expectedTargetSnapshotRef().equals(observation.targetSnapshotRef())
                || !command.riskId().equals(observation.riskId())
                || !command.ruleSetVersion().equals(observation.ruleSetVersion())
                || observation.executionMode() != command.executionMode()
                || observation.sessionRef() != null
                || !observation.requiredCapabilities().contains(TargetCapability.SINGLE_TURN)) {
            throw new IllegalStateException("执行观察的目标或规则版本与任务快照不一致");
        }
        validateObservationEvidence(observation);
    }

    /**
     * 校验 D01 返回的所有证据引用都属于当前物理执行。
     *
     * <p>响应正文在后续读取时还会再次校验，但输入、错误、工具和检索轨迹
     * 也不能仅因为来自端口就直接写入 D02 发现记录。统一在编排器边界检查，
     * 可以避免适配器错误或未来新实现把其他执行的证据带入当前运行。</p>
     */
    private void validateObservationEvidence(TargetObservation observation) {
        String executionId = observation.reference().executionId();
        validateEvidenceReference(
                observation.inputEvidence(), executionId, EvidenceType.PROBE_INPUT, true, "输入");
        validateEvidenceReference(
                observation.responseEvidence(), executionId, EvidenceType.TARGET_RESPONSE, false, "响应");
        validateEvidenceReference(
                observation.errorEvidence(), executionId, EvidenceType.TARGET_ERROR, false, "错误");
        for (EvidenceReference reference : observation.toolTraceEvidence()) {
            validateEvidenceReference(reference, executionId, EvidenceType.TOOL_TRACE, true, "工具轨迹");
        }
        for (EvidenceReference reference : observation.retrievalTraceEvidence()) {
            validateEvidenceReference(reference, executionId, EvidenceType.RETRIEVAL_TRACE, true, "检索轨迹");
        }
    }

    private void validateEvidenceReference(EvidenceReference reference,
                                           String executionId,
                                           EvidenceType expectedType,
                                           boolean required,
                                           String label) {
        if (reference == null) {
            if (required) {
                throw new IllegalStateException(label + "证据引用缺失");
            }
            return;
        }
        if (!PROBE_EXECUTION_OWNER_TYPE.equals(reference.ownerType())
                || !executionId.equals(reference.ownerRef())
                || reference.type() != expectedType) {
            throw new IllegalStateException(label + "证据引用与当前目标执行不一致");
        }
    }

    /** 为同一运行内的同一动作生成稳定的逻辑探测标识，支持幂等重放。 */
    private String deterministicProbeId(String runId, int sequence) {
        return "probe_" + sha256(runId + ":d02:probe:" + sequence).substring(0, 58);
    }

    /** 为同一运行内同一动作生成稳定的疑似发现标识，避免重复处理时重复入库。 */
    private String deterministicFindingId(String runId, String actionId) {
        return "finding_" + sha256(runId + ":d02:finding:" + actionId);
    }

    /** 只把受控错误编码写入运行摘要，避免模型返回正文或敏感片段落库。 */
    private String safeDecisionMessage(AttackDecision decision) {
        String errorCode = decision.errorCode();
        if (errorCode != null && errorCode.matches("[A-Z0-9_]{1,64}")) {
            return "攻击 Agent 生成失败，错误编码=" + errorCode;
        }
        return "攻击 Agent 生成失败";
    }

    /** 使用精确时长比较，避免按整秒截断导致调用越过期限。 */
    private boolean durationExceeded(Instant startedAt, long maxDurationSeconds) {
        return Duration.between(startedAt, Instant.now())
                .compareTo(Duration.ofSeconds(maxDurationSeconds)) >= 0;
    }

    /** 将目标结果映射为动作层的实际停止原因。 */
    private AttackStopReason actualStopReason(TargetObservation observation) {
        return switch (observation.status()) {
            case UNKNOWN -> AttackStopReason.TARGET_RESULT_UNKNOWN;
            case FAILED, CANCELLED -> AttackStopReason.TARGET_EXECUTION_FAILED;
            default -> null;
        };
    }

    /** 终态未知时不能把动作展示为已成功执行。 */
    private String actionStatus(TargetObservation observation) {
        return observation.status() == ProbeExecutionStatus.UNKNOWN
                ? "EXECUTION_UNKNOWN"
                : "EXECUTED";
    }

    private RedTeamRun finish(RedTeamTask task,
                              RedTeamRun currentRun,
                              BudgetLedger ledger,
                              String attemptId,
                              int actionCount,
                              int probeCount,
                              RedTeamRunStatus status,
                              AttackStopReason stopReason,
                              String stopMessage) {
        Instant finishedAt = Instant.now();
        boolean stopRequested = runRecorder.isStopRequested(
                currentRun.tenantId(), currentRun.actorId(), currentRun.runId());

        // 所有结束分支都在这里再次收敛控制面信号。停止请求可能在 Agent
        // 生成、目标调用或证据读取期间到达，不能因为某个较早计算出的业务
        // 原因而被覆盖；期限同样优先于风险命中、策略耗尽和执行未知。
        if (stopRequested) {
            status = RedTeamRunStatus.STOPPED;
            stopReason = AttackStopReason.USER_STOPPED;
            stopMessage = "用户已请求停止运行";
        } else if (currentRun.startedAt() != null
                && durationExceeded(currentRun.startedAt(), task.budgetPolicy().maxDurationSeconds())) {
            status = RedTeamRunStatus.COMPLETED;
            stopReason = AttackStopReason.TIME_LIMIT_REACHED;
            stopMessage = "运行达到任务配置的最长时间";
        }

        RedTeamRun finishedRun = copyRun(
                currentRun,
                status,
                ledger.snapshot(),
                actionCount,
                attemptId,
                stopReason,
                stopMessage,
                stopRequested,
                currentRun.startedAt(),
                finishedAt);
        runRecorder.updateRun(finishedRun);

        RedTeamAttempt finishedAttempt = new RedTeamAttempt(
                attemptId,
                task.taskId(),
                currentRun.runId(),
                1,
                task.riskId(),
                task.strategyVersion(),
                status,
                probeCount,
                stopReason,
                stopMessage,
                currentRun.startedAt() == null ? finishedAt : currentRun.startedAt(),
                finishedAt);
        runRecorder.saveAttempt(finishedAttempt);

        RedTeamTaskStatus taskStatus = switch (status) {
            case BLOCKED -> RedTeamTaskStatus.BLOCKED;
            case FAILED -> RedTeamTaskStatus.FAILED;
            case STOPPED -> RedTeamTaskStatus.STOPPED;
            default -> RedTeamTaskStatus.COMPLETED;
        };
        taskRepository.updateStatus(
                task.tenantId(),
                task.actorId(),
                task.taskId(),
                copyTask(task, taskStatus, stopReason, stopMessage, currentRun.startedAt(), finishedAt));
        return finishedRun;
    }

    private RedTeamRun copyRun(RedTeamRun source,
                               RedTeamRunStatus status,
                               BudgetSnapshot budget,
                               int actionCount,
                               String attemptId,
                               AttackStopReason stopReason,
                               String stopMessage,
                               boolean stopRequested,
                               Instant startedAt,
                               Instant finishedAt) {
        return new RedTeamRun(
                source.runId(),
                source.taskId(),
                source.tenantId(),
                source.actorId(),
                status,
                budget,
                actionCount,
                attemptId,
                stopReason,
                stopMessage,
                stopRequested,
                source.createdAt(),
                startedAt,
                finishedAt);
    }

    private RedTeamTask copyTask(RedTeamTask source,
                                 RedTeamTaskStatus status,
                                 AttackStopReason stopReason,
                                 String stopMessage,
                                 Instant startedAt,
                                 Instant finishedAt) {
        return new RedTeamTask(
                source.taskId(),
                source.idempotencyKey(),
                source.tenantId(),
                source.actorId(),
                source.activityId(),
                source.authorizationRef(),
                source.target(),
                source.targetSnapshotRef(),
                source.riskId(),
                source.ruleSetVersion(),
                source.executionMode(),
                source.strategyVersion(),
                source.seedInput(),
                source.riskIndicators(),
                source.budgetPolicy(),
                status,
                stopReason,
                stopMessage,
                source.createdAt(),
                startedAt,
                finishedAt);
    }

    private String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前Java运行环境不支持SHA-256", ex);
        }
    }

    private String safeMessage(Throwable throwable) {
        // 第三方异常message可能包含输入、响应或凭据片段，不能直接写入运行
        // 摘要和对外接口；详细异常应由受控日志链路按权限记录。
        return throwable == null ? "未知错误" : throwable.getClass().getSimpleName();
    }
}
