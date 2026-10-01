package com.kant.agent.acceptance;

import com.kant.agent.contract.budget.BudgetPolicy;
import com.kant.agent.contract.common.ExecutionContext;
import com.kant.agent.contract.evidence.*;
import com.kant.agent.contract.finding.FindingReviewOpinion;
import com.kant.agent.contract.finding.FindingVerificationReport;
import com.kant.agent.contract.probe.*;
import com.kant.agent.contract.redteam.*;
import com.kant.agent.contract.target.TargetReference;
import com.kant.agent.contract.target.TargetType;
import com.kant.agent.port.evidence.EvidenceReadPort;
import com.kant.agent.port.probe.ProbeExecutionPort;
import com.kant.agent.service.finding.FindingVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** D04证据核对的隔离测试：所有输入/响应都为内存中的虚构事实。 */
class AgentFindingVerifierTest {
    private static final String TENANT = "tenant-test";
    private static final String ACTOR = "actor-test";
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private final ProbeExecutionPort probes = mock(ProbeExecutionPort.class);
    private final EvidenceReadPort evidence = mock(EvidenceReadPort.class);
    private final FindingVerifier verifier = new FindingVerifier(probes, evidence);

    @Test
    @DisplayName("完整单轮证据只建议复现，绝不自动确认风险或调用目标")
    void validatesWithoutCallingTarget() {
        Fixture fixture = fixture(false, ACTOR, false);
        var report = inspect(fixture);
        assertEquals(FindingVerificationReport.Integrity.VALID, report.integrity());
        assertEquals(FindingReviewOpinion.REPLAY_REQUIRED, report.opinion());
        assertEquals(1, report.evidenceSnapshot().size());
        verify(probes, never()).submit(any());
    }

    @Test
    @DisplayName("正文篡改使摘要核对失败，报告不能泄露正文")
    void rejectsTamperedContent() {
        Fixture fixture = fixture(false, ACTOR, false);
        EvidenceReference ref = reference("response-1", "execution-1", EvidenceType.TARGET_RESPONSE,
                "DEMO_SIGNAL_1");
        when(evidence.read(new EvidenceQuery(TENANT, ACTOR, "execution-1", "response-1")))
                .thenReturn(Optional.of(new EvidenceContent(ref, "TAMPERED_BODY_TEST")));
        var report = inspect(fixture);
        assertEquals(FindingVerificationReport.Integrity.INVALID, report.integrity());
        assertFalse(report.reason().contains("TAMPERED_BODY_TEST"));
    }

    @Test
    @DisplayName("缺少物理执行是证据不足，不是无风险")
    void reportsMissingExecution() {
        Fixture fixture = fixture(false, ACTOR, false);
        when(probes.find(new ProbeLookup(TENANT, ACTOR, "execution-1"))).thenReturn(Optional.empty());
        var report = inspect(fixture);
        assertEquals(FindingVerificationReport.Integrity.INCOMPLETE, report.integrity());
        assertEquals(FindingReviewOpinion.INSUFFICIENT, report.opinion());
    }

    @Test
    @DisplayName("不能用另一操作者的物理事实确认当前发现")
    void rejectsForeignActor() {
        var report = inspect(fixture(false, "actor-other", false));
        assertEquals(FindingVerificationReport.Integrity.INVALID, report.integrity());
    }

    @Test
    @DisplayName("多轮必须包含截至触发动作的完整成功轨迹")
    void validatesFullMultiTurnTrajectory() {
        var report = inspect(fixture(true, ACTOR, false));
        assertEquals(FindingVerificationReport.Integrity.VALID, report.integrity());
        assertEquals(2, report.evidenceSnapshot().size());
    }

    @Test
    @DisplayName("只提供多轮最后一轮证据不能冒充完整轨迹")
    void rejectsTruncatedMultiTurnEvidence() {
        var report = inspect(fixture(true, ACTOR, true));
        assertEquals(FindingVerificationReport.Integrity.INVALID, report.integrity());
    }

    private FindingVerificationReport inspect(Fixture fixture) {
        return verifier.inspect(TENANT, ACTOR, fixture.finding(), fixture.task(), fixture.actions());
    }

    /** 创建真实契约对象，端口只返回内存事实，不连接Spring或供应商。 */
    private Fixture fixture(boolean multiTurn, String observationActor, boolean onlyLastEvidence) {
        ExecutionMode mode = multiTurn ? ExecutionMode.ADAPTIVE_MULTI_TURN : ExecutionMode.ADAPTIVE_SINGLE_TURN;
        var target = new TargetReference(TargetType.MODEL, "1");
        var task = new RedTeamTask("task-1", "task-key", TENANT, ACTOR, "activity-1", "approved-test",
                target, "snapshot-1", "fictional-risk", "rules-v1", mode, "strategy-v1", "虚构种子",
                List.of("DEMO_SIGNAL"), new BudgetPolicy(3, 0, 1, 3, 1000, null, null, 120, 1, 0),
                RedTeamTaskStatus.COMPLETED, null, null, NOW, NOW, NOW);
        List<RedTeamActionRecord> actions = new ArrayList<>();
        List<String> refs = new ArrayList<>();
        int turns = multiTurn ? 2 : 1;
        for (int sequence = 1; sequence <= turns; sequence++) {
            String executionId = "execution-" + sequence;
            String probeId = "probe-" + sequence;
            String input = "虚构输入" + sequence;
            String response = "DEMO_SIGNAL_" + sequence;
            EvidenceReference inputRef = reference("input-" + sequence, executionId, EvidenceType.PROBE_INPUT, input);
            EvidenceReference responseRef = reference("response-" + sequence, executionId, EvidenceType.TARGET_RESPONSE, response);
            var context = new ExecutionContext(TENANT, observationActor, "activity-1", "task-1", "run-1",
                    "attempt-1", "trace-1", "approved-test");
            var observation = new TargetObservation(context, new ProbeExecutionReference(probeId, executionId),
                    "key-" + sequence, ProbeExecutionStatus.SUCCEEDED, target, "snapshot-1", "fictional-risk",
                    "rules-v1", multiTurn ? ExecutionMode.ADAPTIVE_MULTI_TURN : ExecutionMode.STATIC_SINGLE_TURN,
                    Set.of(), multiTurn ? "session-1" : null, null, inputRef, responseRef, null, List.of(),
                    List.of(), EvidenceCompleteness.COMPLETE, 1L,
                    new UsageRecord(1, 0, 10L, 10L, null, null, UsageMeasurement.ESTIMATED),
                    null, null, false, NOW, NOW, NOW);
            when(probes.find(new ProbeLookup(TENANT, ACTOR, executionId))).thenReturn(Optional.of(observation));
            when(evidence.read(new EvidenceQuery(TENANT, ACTOR, executionId, inputRef.evidenceRef())))
                    .thenReturn(Optional.of(new EvidenceContent(inputRef, input)));
            when(evidence.read(new EvidenceQuery(TENANT, ACTOR, executionId, responseRef.evidenceRef())))
                    .thenReturn(Optional.of(new EvidenceContent(responseRef, response)));
            actions.add(new RedTeamActionRecord("action-" + sequence, "task-1", "run-1", "attempt-1", sequence,
                    AttackActionType.SEND_INPUT, "strategy-v1", inputRef.contentHash(), "虚构证据核对", List.of(),
                    null, null, inputRef.evidenceRef(), probeId, executionId, ProbeExecutionStatus.SUCCEEDED,
                    responseRef.evidenceRef(), null, sequence == turns, "EXECUTED", NOW));
            if (!onlyLastEvidence || sequence == turns) {
                refs.add(inputRef.evidenceRef());
                refs.add(responseRef.evidenceRef());
            }
        }
        var finding = new SuspectedFinding("finding-1", "task-1", "run-1", "attempt-1", "action-" + turns,
                "fictional-risk", refs, "虚构信号，不代表漏洞", "SUSPECTED", NOW);
        return new Fixture(task, finding, List.copyOf(actions));
    }

    private EvidenceReference reference(String id, String execution, EvidenceType type, String content) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
            return new EvidenceReference(id, type, "PROBE_EXECUTION", execution, "text/plain; charset=UTF-8",
                    hash, EvidenceDataClass.RESTRICTED, NOW);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("测试环境缺少SHA-256", exception);
        }
    }

    private record Fixture(RedTeamTask task, SuspectedFinding finding, List<RedTeamActionRecord> actions) {}
}
