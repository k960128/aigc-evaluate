package com.kant.agent.service.finding;

import com.kant.agent.contract.evidence.*;
import com.kant.agent.contract.finding.*;
import com.kant.agent.contract.probe.*;
import com.kant.agent.contract.redteam.*;
import com.kant.agent.port.evidence.EvidenceReadPort;
import com.kant.agent.port.probe.ProbeExecutionPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * D04首个确定性复核器：独立读取真实执行和原文，核对完整轨迹的引用与哈希。
 * 不调用目标、攻击模型或Judge，不产生费用，也不自动确认风险或宣布修复。
 * 所有目标正文仅用作待检查数据，不解析为指令。
 */
public final class FindingVerifier {
    public static final String VERSION = "evidence-integrity-v1";
    private final ProbeExecutionPort probes;
    private final EvidenceReadPort evidence;

    public FindingVerifier(ProbeExecutionPort probes, EvidenceReadPort evidence) {
        this.probes = probes;
        this.evidence = evidence;
    }

    /** 单轮只核对触发动作；多轮必须核对同一尝试截至触发动作的全部成功轮次。 */
    public FindingVerificationReport inspect(String tenant, String actor, SuspectedFinding finding, RedTeamTask task,
                                             List<RedTeamActionRecord> actions) {
        List<FindingEvidenceSnapshot> snapshots = new ArrayList<>();
        try {
            RedTeamActionRecord trigger = actions.stream().filter(a -> a.actionId().equals(finding.actionId())).findFirst()
                    .orElseThrow(() -> new EvidenceProblem(false));
            if (!trigger.riskObserved() || !trigger.attemptId().equals(finding.attemptId())
                    || !task.taskId().equals(finding.taskId()) || !task.riskId().equals(finding.riskId()))
                throw new EvidenceProblem(true);
            List<RedTeamActionRecord> trajectory = task.executionMode() == ExecutionMode.ADAPTIVE_MULTI_TURN
                    ? actions.stream().filter(a -> a.attemptId().equals(finding.attemptId()) && a.sequence() <= trigger.sequence()
                                                   && "EXECUTED".equals(a.actionStatus())).sorted(Comparator.comparingInt(RedTeamActionRecord::sequence)).toList()
                    : List.of(trigger);
            if (trajectory.isEmpty()) throw new EvidenceProblem(false);
            List<String> expectedRefs = new ArrayList<>();
            String sessionRef = null;
            for (RedTeamActionRecord action : trajectory) {
                if (action.executionId() == null) throw new EvidenceProblem(false);
                TargetObservation observation = probes.find(new ProbeLookup(tenant, actor, action.executionId()))
                        .orElseThrow(() -> new EvidenceProblem(false));
                if (observation.status() != ProbeExecutionStatus.SUCCEEDED) throw new EvidenceProblem(false);
                ExecutionMode expectedMode = task.executionMode() == ExecutionMode.ADAPTIVE_MULTI_TURN
                        ? ExecutionMode.ADAPTIVE_MULTI_TURN : ExecutionMode.STATIC_SINGLE_TURN;
                if (!observation.context().tenantId().equals(tenant) || !observation.context().actorId().equals(actor)
                        || !observation.context().runId().equals(finding.runId())
                        || !observation.context().taskId().equals(finding.taskId())
                        || !observation.context().attemptId().equals(finding.attemptId())
                        || !observation.target().equals(task.target()) || !observation.targetSnapshotRef().equals(task.targetSnapshotRef())
                        || !observation.riskId().equals(task.riskId()) || !observation.ruleSetVersion().equals(task.ruleSetVersion())
                        || observation.executionMode() != expectedMode || !action.runId().equals(finding.runId())
                        || !Objects.equals(action.probeId(), observation.reference().probeId()))
                    throw new EvidenceProblem(true);
                if (expectedMode == ExecutionMode.ADAPTIVE_MULTI_TURN) {
                    if (observation.sessionRef() == null || sessionRef != null && !sessionRef.equals(observation.sessionRef()))
                        throw new EvidenceProblem(true);
                    sessionRef = observation.sessionRef();
                }
                EvidenceReference input = read(tenant, actor, observation, observation.inputEvidence(), EvidenceType.PROBE_INPUT);
                EvidenceReference response = read(tenant, actor, observation, observation.responseEvidence(), EvidenceType.TARGET_RESPONSE);
                if (!input.contentHash().equals(action.inputHash()) || !input.evidenceRef().equals(action.inputEvidenceRef())
                        || !response.evidenceRef().equals(action.responseEvidenceRef()))
                    throw new EvidenceProblem(true);
                snapshots.add(new FindingEvidenceSnapshot(action.sequence(), action.executionId(), input, response));
                expectedRefs.add(input.evidenceRef());
                expectedRefs.add(response.evidenceRef());
            }
            // 不允许只拿最后一轮冒充完整多轮，也不允许混入另一运行的证据。
            if (finding.evidenceRefs().size() != expectedRefs.size()
                    || !new HashSet<>(finding.evidenceRefs()).equals(new HashSet<>(expectedRefs)))
                throw new EvidenceProblem(true);
            return report(task, FindingVerificationReport.Integrity.VALID,
                    "执行归属、完整轨迹和原文摘要一致；尚需风险规则复核或独立复现", snapshots);
        } catch (EvidenceProblem problem) {
            return report(task, problem.invalid ? FindingVerificationReport.Integrity.INVALID : FindingVerificationReport.Integrity.INCOMPLETE,
                    problem.invalid ? "证据索引、轨迹边界或正文摘要不一致，不能自动确认风险" : "执行或证据缺失，不能解释为无风险或已修复", snapshots);
        }
    }

    private FindingVerificationReport report(RedTeamTask task, FindingVerificationReport.Integrity integrity, String reason,
                                             List<FindingEvidenceSnapshot> snapshots) {
        return new FindingVerificationReport(integrity, integrity == FindingVerificationReport.Integrity.VALID
                ? FindingReviewOpinion.REPLAY_REQUIRED : FindingReviewOpinion.INSUFFICIENT, VERSION, reason,
                task.targetSnapshotRef(), task.ruleSetVersion(), snapshots);
    }

    /** 同时核对索引类型、物理执行归属和正文SHA-256，不返回正文。 */
    private EvidenceReference read(String tenant, String actor, TargetObservation observation, EvidenceReference ref, EvidenceType type) {
        if (ref == null) throw new EvidenceProblem(false);
        if (ref.type() != type || !"PROBE_EXECUTION".equals(ref.ownerType())
                || !ref.ownerRef().equals(observation.reference().executionId())) throw new EvidenceProblem(true);
        EvidenceContent content = evidence.read(new EvidenceQuery(tenant, actor, ref.ownerRef(), ref.evidenceRef()))
                .orElseThrow(() -> new EvidenceProblem(false));
        if (!ref.equals(content.reference()) || !digest(content.content()).equals(ref.contentHash()))
            throw new EvidenceProblem(true);
        return ref;
    }

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256不可用", ex);
        }
    }

    /** 仅包含安全错误分类，不把不可信正文带入异常或日志。 */
    private static final class EvidenceProblem extends RuntimeException {
        final boolean invalid;

        EvidenceProblem(boolean invalid) {
            super("证据一致性检查未通过");
            this.invalid = invalid;
        }
    }
}
