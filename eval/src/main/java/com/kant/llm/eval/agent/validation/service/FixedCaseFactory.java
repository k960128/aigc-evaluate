package com.kant.llm.eval.agent.validation.service;

import com.kant.agent.contract.finding.FindingVerificationReport;
import com.kant.agent.contract.probe.ExecutionMode;
import com.kant.agent.contract.target.TargetSnapshot;
import com.kant.agent.contract.validation.FixedCaseDefinition;
import com.kant.agent.service.finding.FindingVerifier;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.finding.error.AgentFindingErrorCode;
import com.kant.llm.eval.agent.finding.service.FindingRecordService;
import com.kant.llm.eval.agent.probe.service.AgentProbeRecordService;
import com.kant.llm.eval.agent.validation.support.D04JsonSupport;
import org.springframework.stereotype.Component;

/** 将真实发生的完整轨迹冻结为候选配置，不生成输入，不采当前配置冒充历史条件。 */
@Component
public class FixedCaseFactory {
    private final FindingRecordService findings;
    private final FindingVerifier verifier;
    private final AgentProbeRecordService probes;
    private final D04JsonSupport json;
    public FixedCaseFactory(FindingRecordService findings,FindingVerifier verifier,AgentProbeRecordService probes,D04JsonSupport json) {
        this.findings=findings; this.verifier=verifier; this.probes=probes; this.json=json;
    }

    /** 仅在全部原始输入和响应证据完整时允许沉淀固定轨迹。 */
    public FixedCaseDefinition create(String tenant,String actor,String root,String occurrence,String preconditions) {
        var source=findings.memberSource(tenant,actor,root,occurrence);
        var report=verifier.inspect(tenant,actor,source.finding(),source.task(),source.actions());
        if(report.integrity()!=FindingVerificationReport.Integrity.VALID) throw error(AgentFindingErrorCode.EVIDENCE_INSUFFICIENT);
        var first=report.evidenceSnapshot().get(0);
        var execution=probes.findAuthorized(tenant,actor,first.executionId())
                .orElseThrow(() -> error(AgentFindingErrorCode.EVIDENCE_INSUFFICIENT));
        TargetSnapshot historical=json.read(execution.getTargetSnapshotJson(),TargetSnapshot.class);
        if(!historical.snapshotRef().equals(source.task().targetSnapshotRef()) || !historical.target().equals(source.task().target()))
            throw error(AgentFindingErrorCode.EVIDENCE_INSUFFICIENT);
        var snapshots=report.evidenceSnapshot();
        var steps=java.util.stream.IntStream.range(0,snapshots.size()).mapToObj(i -> {
            var s=snapshots.get(i);
            return new FixedCaseDefinition.Step(i+1,s.executionId(),s.input().evidenceRef(),s.input().contentHash());
        }).toList();
        return new FixedCaseDefinition(root,occurrence,historical.target(),historical.snapshotRef(),historical.environment(),
                historical.identityRef(),source.task().riskId(),source.task().ruleSetVersion(),
                source.task().executionMode()==ExecutionMode.ADAPTIVE_MULTI_TURN,preconditions,steps,
                "literal-response-signal-v1",source.task().riskIndicators());
    }
    private ClientException error(AgentFindingErrorCode code) { return new ClientException(code.message(),code); }
}
