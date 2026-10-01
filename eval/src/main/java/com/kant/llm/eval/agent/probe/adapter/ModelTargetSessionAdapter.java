package com.kant.llm.eval.agent.probe.adapter;

import com.kant.agent.contract.common.ExecutionContext;
import com.kant.agent.contract.redteam.AttackSession;
import com.kant.agent.contract.redteam.AttackSessionStatus;
import com.kant.agent.contract.target.TargetSessionMessage;
import com.kant.agent.contract.target.TargetSessionReference;
import com.kant.agent.contract.target.TargetSnapshot;
import com.kant.agent.port.target.TargetSessionPort;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.probe.error.AgentProbeErrorCode;
import com.kant.llm.eval.agent.probe.service.AgentProbeRecordService;
import com.kant.llm.eval.agent.redteam.service.AgentRedTeamRecordService;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * D03 模型目标的线性会话适配器。
 *
 * <p>当前模型客户端没有稳定的供应商会话 ID，因此平台保存会话控制面，并在
 * 每次目标调用时把已经确认的 user/assistant 消息作为 Spring AI 消息列表发送。
 * 这是真实的消息上下文重建，不是把多次独立调用的结果当成多个单轮结果。</p>
 *
 * <p>重置和分支仍然明确拒绝，避免在没有目标语义保证时伪造这些能力。</p>
 */
@Component
@Primary
public class ModelTargetSessionAdapter implements TargetSessionPort {

    private final AgentRedTeamRecordService sessionRecorder;
    private final AgentProbeRecordService probeRecordService;

    public ModelTargetSessionAdapter(AgentRedTeamRecordService sessionRecorder,
                                     AgentProbeRecordService probeRecordService) {
        this.sessionRecorder = sessionRecorder;
        this.probeRecordService = probeRecordService;
    }

    @Override
    public TargetSessionReference open(ExecutionContext context, TargetSnapshot targetSnapshot) {
        // 会话、初始预算及检查点只能由编排器原子创建，不能旁路建立孤立会话。
        throw unsupported("独立创建，请通过红队运行启动");
    }

    @Override
    public TargetSessionReference find(ExecutionContext context, String sessionRef) {
        AttackSession session = sessionRecorder.findSession(
                        context.tenantId(), context.actorId(), sessionRef)
                .orElseThrow(() -> new ClientException(
                        "目标会话不存在或无权访问，sessionRef=" + sessionRef,
                        AgentProbeErrorCode.PROBE_NOT_FOUND));
        if (!session.taskId().equals(context.taskId())
                || !session.runId().equals(context.runId())
                || !session.attemptId().equals(context.attemptId())) {
            throw new ClientException(
                    "目标会话不属于当前评测运行",
                    AgentProbeErrorCode.PROBE_STATE_CONFLICT);
        }
        if (session.status() == AttackSessionStatus.EXPIRED) {
            throw new ClientException(
                    "目标会话已过期，不能继续追加消息",
                    AgentProbeErrorCode.TARGET_CAPABILITY_UNSUPPORTED);
        }
        if (session.status() != AttackSessionStatus.ACTIVE || session.stopRequested()) {
            throw new ClientException(
                    "目标会话已经结束，不能继续追加消息",
                    AgentProbeErrorCode.PROBE_STATE_CONFLICT);
        }
        return new TargetSessionReference(
                session.sessionId(), null, session.targetSnapshotRef(), session.createdAt());
    }

    @Override
    public List<TargetSessionMessage> history(
            ExecutionContext context,
            TargetSessionReference session) {
        TargetSessionReference current = find(context, session.sessionRef());
        if (!current.targetSnapshotRef().equals(session.targetSnapshotRef()))
            throw new ClientException("会话快照不一致", AgentProbeErrorCode.PROBE_STATE_CONFLICT);
        List<TargetSessionMessage> messages = probeRecordService.listSessionMessages(
                context.tenantId(), context.actorId(), session.sessionRef());
        AttackSession state = sessionRecorder.findSession(context.tenantId(),context.actorId(),session.sessionRef()).orElseThrow();
        if (messages.size()!=state.turnCount()*2)
            throw new ClientException("历史消息与已确认检查点不一致", AgentProbeErrorCode.PROBE_STATE_CONFLICT);
        return messages;
    }

    @Override
    public TargetSessionReference reset(ExecutionContext context, TargetSessionReference session) {
        throw unsupported("重置");
    }

    @Override
    public TargetSessionReference branch(ExecutionContext context, TargetSessionReference session) {
        throw unsupported("分支");
    }

    @Override
    public void close(ExecutionContext context, TargetSessionReference session) {
        throw unsupported("独立关闭，请通过运行停止命令");
    }

    private ClientException unsupported(String operation) {
        return new ClientException(
                "当前目标适配器不支持会话" + operation + "操作",
                AgentProbeErrorCode.TARGET_CAPABILITY_UNSUPPORTED);
    }
}
