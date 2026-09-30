package com.kant.llm.eval.agent.redteam.application;

import com.kant.agent.contract.redteam.RedTeamTask;
import com.kant.agent.contract.redteam.RedTeamTaskCommand;
import com.kant.agent.contract.redteam.RedTeamTaskStatus;
import com.kant.agent.contract.target.TargetCapability;
import com.kant.agent.contract.target.TargetReference;
import com.kant.agent.contract.target.TargetSnapshot;
import com.kant.agent.contract.target.TargetStatus;
import com.kant.agent.port.redteam.RedTeamRunRecorder;
import com.kant.agent.port.redteam.RedTeamTaskRepository;
import com.kant.agent.service.redteam.RedTeamRunStateConflictException;
import com.kant.agent.service.redteam.EvaluationOrchestrator;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.probe.application.ProbePrincipal;
import com.kant.llm.eval.agent.probe.application.ProbePrincipalProvider;
import com.kant.llm.eval.agent.redteam.dto.req.CreateRedTeamTaskRequest;
import com.kant.llm.eval.agent.redteam.dto.resp.RedTeamRunResponse;
import com.kant.llm.eval.agent.redteam.dto.resp.RedTeamTaskResponse;
import com.kant.llm.eval.agent.redteam.error.AgentRedTeamErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * D02 自动红队任务应用服务。
 *
 * <p>该服务只负责身份、HTTP 请求和领域端口之间的转换。攻击循环由
 * {@link EvaluationOrchestrator} 负责，目标模型调用仍由 D01 端口负责。</p>
 */
@Service
public class RedTeamApplicationService {

    private final ProbePrincipalProvider principalProvider;
    private final com.kant.agent.port.target.TargetCapabilityPort targetCapabilityPort;
    private final RedTeamTaskRepository taskRepository;
    private final RedTeamRunRecorder runRecorder;
    private final EvaluationOrchestrator orchestrator;

    public RedTeamApplicationService(ProbePrincipalProvider principalProvider,
                                    com.kant.agent.port.target.TargetCapabilityPort targetCapabilityPort,
                                    RedTeamTaskRepository taskRepository,
                                    RedTeamRunRecorder runRecorder,
                                    EvaluationOrchestrator orchestrator) {
        this.principalProvider = principalProvider;
        this.targetCapabilityPort = targetCapabilityPort;
        this.taskRepository = taskRepository;
        this.runRecorder = runRecorder;
        this.orchestrator = orchestrator;
    }

    /** 创建或幂等返回一份 D02 任务配置。 */
    public RedTeamTaskResponse createTask(CreateRedTeamTaskRequest request) {
        if (request == null || request.getBudgetPolicy() == null) {
            throw new ClientException(
                    "budgetPolicy不能为空",
                    AgentRedTeamErrorCode.INVALID_TASK_REQUEST);
        }
        ProbePrincipal principal = principalProvider.currentPrincipal();
        if (request.getBudgetPolicy().getMaxCost() != null) {
            // 当前模型客户端没有统一价格目录，也没有返回可核验的费用计量。
            // 接受该字段会让前端误以为费用上限已经生效，因此在任务创建阶段拒绝。
            throw new ClientException(
                    "当前目标适配器暂不支持费用预算，请先去掉maxCost配置",
                    AgentRedTeamErrorCode.BUDGET_METER_UNSUPPORTED);
        }
        TargetReference target = new TargetReference(
                com.kant.agent.contract.target.TargetType.MODEL,
                String.valueOf(request.getTargetModelId()));

        // 幂等重放必须返回首次创建时的不可变任务快照。目标当前快照可能已经
        // 变化，但这不应让同一个幂等请求被误判为另一份配置或被迫重新探测。
        Optional<RedTeamTask> existing = findTaskByIdempotencyKey(
                principal.tenantId(), request.getIdempotencyKey());
        if (existing.isPresent()) {
            RedTeamTask existingTask = existing.get();
            RedTeamTaskCommand replayCommand;
            try {
                replayCommand = new RedTeamTaskCommand(
                        request.getIdempotencyKey(),
                        existingTask.taskId(),
                        principal.tenantId(),
                        principal.actorId(),
                        request.getActivityId(),
                        request.getAuthorizationRef(),
                        target,
                        existingTask.targetSnapshotRef(),
                        request.getRiskId(),
                        request.getRuleSetVersion(),
                        request.getExecutionMode(),
                        request.getStrategyVersion(),
                        request.getSeedInput(),
                        request.getRiskIndicators(),
                        request.getBudgetPolicy().toDomain());
            } catch (IllegalArgumentException ex) {
                throw new ClientException(ex.getMessage(), AgentRedTeamErrorCode.INVALID_TASK_REQUEST);
            }
            ensureSameTaskConfiguration(existingTask, replayCommand);
            return RedTeamTaskResponse.from(existingTask);
        }

        TargetSnapshot snapshot;
        try {
            snapshot = targetCapabilityPort.getSnapshot(
                    principal.tenantId(), principal.actorId(), target);
        } catch (ClientException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw persistenceError("目标能力快照读取失败", ex);
        }
        if (snapshot == null || !target.equals(snapshot.target())) {
            throw new ClientException(
                    "目标能力适配器返回的快照与请求目标不一致",
                    AgentRedTeamErrorCode.TARGET_SNAPSHOT_INVALID);
        }
        if (snapshot.status() != TargetStatus.AVAILABLE
                 || !snapshot.capabilities().supports(TargetCapability.SINGLE_TURN)) {
            throw new ClientException(
                    "目标当前不支持D02所需的MODEL静态单轮能力",
                    AgentRedTeamErrorCode.TARGET_CAPABILITY_UNSUPPORTED);
        }

        RedTeamTaskCommand command;
        try {
            command = new RedTeamTaskCommand(
                    request.getIdempotencyKey(),
                    newTaskId(),
                    principal.tenantId(),
                    principal.actorId(),
                    request.getActivityId(),
                    request.getAuthorizationRef(),
                    target,
                    snapshot.snapshotRef(),
                    request.getRiskId(),
                    request.getRuleSetVersion(),
                    request.getExecutionMode(),
                    request.getStrategyVersion(),
                    request.getSeedInput(),
                    request.getRiskIndicators(),
                    request.getBudgetPolicy().toDomain());
        } catch (IllegalArgumentException ex) {
            throw new ClientException(ex.getMessage(), AgentRedTeamErrorCode.INVALID_TASK_REQUEST);
        }

        RedTeamTask task = RedTeamTask.ready(command, Instant.now());
        try {
            taskRepository.create(task);
        } catch (DuplicateKeyException ex) {
            // 并发创建由数据库唯一键选出唯一胜者，随后读取胜者配置。
            RedTeamTask winner = findTaskByIdempotencyKey(
                            principal.tenantId(), request.getIdempotencyKey())
                    .orElseThrow(() -> new ClientException(
                            "任务幂等竞争后无法读取已创建任务",
                            AgentRedTeamErrorCode.RUN_PERSISTENCE_ERROR));
            ensureSameTaskConfiguration(winner, command);
            task = winner;
        } catch (ClientException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw persistenceError("自动红队任务记录保存失败", ex);
        }
        return RedTeamTaskResponse.from(task);
    }

    /** 查询当前操作者拥有的任务配置和运行汇总状态。 */
    public RedTeamTaskResponse getTask(String taskId) {
        ProbePrincipal principal = principalProvider.currentPrincipal();
        RedTeamTask task = loadTask(principal.tenantId(), principal.actorId(), taskId);
        return RedTeamTaskResponse.from(task);
    }

    /** 启动一次新的 D02 运行；重新运行使用新的 runId。 */
    public RedTeamRunResponse startRun(String taskId) {
        ProbePrincipal principal = principalProvider.currentPrincipal();
        RedTeamTask task = loadTask(principal.tenantId(), principal.actorId(), taskId);
        if (task.status() == RedTeamTaskStatus.RUNNING) {
            throw new ClientException(
                    "自动红队任务已有运行正在执行，taskId=" + taskId,
                    AgentRedTeamErrorCode.TASK_ALREADY_RUNNING);
        }
        if (task.status() == RedTeamTaskStatus.BLOCKED) {
            // D02 尚未实现未知结果核对和恢复，不能直接重试可能已经执行的目标请求。
            throw new ClientException(
                    "自动红队任务处于BLOCKED状态，请先核对目标结果，taskId=" + taskId,
                    AgentRedTeamErrorCode.RUN_STATE_CONFLICT);
        }
        String runId = newRunId();
        try {
            var run = orchestrator.start(
                    principal.tenantId(), principal.actorId(), taskId, runId);
            return RedTeamRunResponse.from(
                    run,
                    runRecorder.listActions(principal.tenantId(), principal.actorId(), runId),
                    runRecorder.listFindings(principal.tenantId(), principal.actorId(), runId),
                    runRecorder.listAttempts(principal.tenantId(), principal.actorId(), runId));
        } catch (RedTeamRunStateConflictException ex) {
            throw new ClientException(ex.getMessage(), AgentRedTeamErrorCode.RUN_STATE_CONFLICT);
        } catch (ClientException ex) {
            throw ex;
        } catch (IllegalStateException ex) {
            // 运行启动过程中的其他IllegalStateException通常来自记录写入、
            // 状态检查点或账本持久化失败，不能伪装成用户的并发状态冲突。
            throw new ClientException(
                    "自动红队运行记录保存失败",
                    AgentRedTeamErrorCode.RUN_PERSISTENCE_ERROR);
        } catch (RuntimeException ex) {
            // 适配器或持久化框架的其他运行时异常也不能直接穿透到接口层；
            // 已受理的运行事实仍以数据库状态为准，接口只返回稳定的服务端错误码。
            throw new ClientException(
                    "自动红队运行记录保存失败",
                    AgentRedTeamErrorCode.RUN_PERSISTENCE_ERROR);
        }
    }

    /** 查询运行状态、动作轨迹和疑似发现。 */
    public RedTeamRunResponse getRun(String runId) {
        ProbePrincipal principal = principalProvider.currentPrincipal();
        try {
            var run = runRecorder.findRun(principal.tenantId(), principal.actorId(), runId)
                    .orElseThrow(() -> new ClientException(
                            "自动红队运行不存在，runId=" + runId,
                            AgentRedTeamErrorCode.RUN_NOT_FOUND));
            return RedTeamRunResponse.from(
                    run,
                    runRecorder.listActions(principal.tenantId(), principal.actorId(), runId),
                    runRecorder.listFindings(principal.tenantId(), principal.actorId(), runId),
                    runRecorder.listAttempts(principal.tenantId(), principal.actorId(), runId));
        } catch (ClientException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw persistenceError("自动红队运行记录读取失败", ex);
        }
    }

    /** 记录用户停止意图并返回最新运行状态。 */
    public RedTeamRunResponse requestStop(String runId) {
        ProbePrincipal principal = principalProvider.currentPrincipal();
        try {
            runRecorder.findRun(principal.tenantId(), principal.actorId(), runId)
                    .orElseThrow(() -> new ClientException(
                            "自动红队运行不存在，runId=" + runId,
                            AgentRedTeamErrorCode.RUN_NOT_FOUND));
            runRecorder.requestStop(principal.tenantId(), principal.actorId(), runId);
        } catch (ClientException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw persistenceError("自动红队停止意图保存失败", ex);
        }
        return getRun(runId);
    }

    /** 读取任务并把数据库或快照解析异常转换为稳定的接口错误码。 */
    private RedTeamTask loadTask(String tenantId, String actorId, String taskId) {
        try {
            return taskRepository.find(tenantId, actorId, taskId)
                    .orElseThrow(() -> new ClientException(
                            "自动红队任务不存在，taskId=" + taskId,
                            AgentRedTeamErrorCode.TASK_NOT_FOUND));
        } catch (ClientException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw persistenceError("自动红队任务记录读取失败", ex);
        }
    }

    /** 按幂等键读取任务，并统一处理数据库或历史快照解析异常。 */
    private Optional<RedTeamTask> findTaskByIdempotencyKey(String tenantId,
                                                            String idempotencyKey) {
        try {
            return taskRepository.findByIdempotencyKey(tenantId, idempotencyKey);
        } catch (RuntimeException ex) {
            throw persistenceError("自动红队任务幂等记录读取失败", ex);
        }
    }

    /** 创建统一的持久化失败异常；详细原因只保留在受控服务端日志链路。 */
    private ClientException persistenceError(String message, RuntimeException cause) {
        return new ClientException(message, cause, AgentRedTeamErrorCode.RUN_PERSISTENCE_ERROR);
    }

    /** 校验同一幂等键是否仍对应完全相同的任务配置。 */
    private void ensureSameTaskConfiguration(RedTeamTask existing, RedTeamTaskCommand command) {
        boolean same = Objects.equals(existing.tenantId(), command.tenantId())
                && Objects.equals(existing.actorId(), command.actorId())
                && Objects.equals(existing.target(), command.target())
                && Objects.equals(existing.targetSnapshotRef(), command.targetSnapshotRef())
                && Objects.equals(existing.activityId(), command.activityId())
                && Objects.equals(existing.authorizationRef(), command.authorizationRef())
                && Objects.equals(existing.riskId(), command.riskId())
                && Objects.equals(existing.ruleSetVersion(), command.ruleSetVersion())
                && Objects.equals(existing.executionMode(), command.executionMode())
                && Objects.equals(existing.strategyVersion(), command.strategyVersion())
                && Objects.equals(existing.seedInput(), command.seedInput())
                && Objects.equals(existing.riskIndicators(), command.riskIndicators())
                && Objects.equals(existing.budgetPolicy(), command.budgetPolicy());
        if (!same) {
            throw new ClientException(
                    "同一幂等键已用于不同自动红队配置",
                    AgentRedTeamErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }

    private String newTaskId() {
        return "rtask_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String newRunId() {
        return "rrun_" + UUID.randomUUID().toString().replace("-", "");
    }
}
