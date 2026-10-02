package com.kant.llm.eval.agent.redteam.service;
import com.kant.agent.service.redteam.MultiTurnEvaluationOrchestrator;
import com.kant.llm.eval.agent.probe.application.ProbePrincipalProvider;
import com.kant.llm.eval.agent.redteam.dto.req.RedTeamRunCommandRequest;
import com.kant.llm.eval.agent.redteam.dto.resp.RedTeamCommandReceipt;
import org.springframework.stereotype.Service;
/** D03用户控制入口；只受理命令，不在HTTP线程推进攻击循环。 */
@Service
public class TaskCommandService {
    private final ProbePrincipalProvider principals;
    private final AgentRedTeamRecordService records;
    private final MultiTurnEvaluationOrchestrator orchestrator;
    public TaskCommandService(ProbePrincipalProvider principals,AgentRedTeamRecordService records,
            MultiTurnEvaluationOrchestrator orchestrator) {
        this.principals=principals; this.records=records; this.orchestrator=orchestrator;
    }
    /** 身份来自认证上下文，幂等和版本检查在同一个数据库事务中完成。 */
    public RedTeamCommandReceipt submit(String runId,RedTeamRunCommandRequest request) {
        var principal=principals.currentPrincipal();
        return records.acceptCommand(principal.tenantId(),principal.actorId(),runId,request,
                () -> orchestrator.validateResume(principal.tenantId(),principal.actorId(),runId));
    }
}
