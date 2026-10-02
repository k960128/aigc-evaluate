package com.kant.llm.eval.agent.validation.worker;

import com.kant.agent.contract.assessment.*;
import com.kant.agent.contract.common.ExecutionContext;
import com.kant.agent.contract.evidence.*;
import com.kant.agent.contract.probe.*;
import com.kant.agent.contract.target.TargetCapability;
import com.kant.agent.contract.validation.*;
import com.kant.agent.port.evidence.EvidenceReadPort;
import com.kant.agent.port.probe.ProbeExecutionPort;
import com.kant.agent.service.budget.TokenEstimator;
import com.kant.llm.eval.agent.validation.dao.entity.AgentValidationRunDO;
import com.kant.llm.eval.agent.validation.service.ValidationRunRecordService;
import com.kant.llm.eval.agent.validation.support.D04JsonSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 固定轨迹后台执行器，不调用攻击Agent生成新输入；所有恢复以数据库事实为准。 */
@Slf4j @Component
public class ValidationRunWorker {
    private final ValidationRunRecordService records;
    private final ProbeExecutionPort probes;
    private final EvidenceReadPort evidence;
    private final D04JsonSupport json;
    private final TaskExecutor executor;
    private final Map<String,AgentValidationRunDO> active=new ConcurrentHashMap<>();
    public ValidationRunWorker(ValidationRunRecordService records,ProbeExecutionPort probes,EvidenceReadPort evidence,
            D04JsonSupport json,@Qualifier("validationWorkerExecutor") TaskExecutor executor) {
        this.records=records; this.probes=probes; this.evidence=evidence; this.json=json; this.executor=executor;
    }
    /** 使用独立有限线程池，BLOCKED只做迟到事实查询，不重复调用外部模型。 */
    // @Scheduled(fixedDelayString="${eval.agent.validation.recovery-interval-ms:5000}",initialDelayString="${eval.agent.validation.initial-delay-ms:5000}")
    public void dispatch() {
        for(var row:records.recoverable()) {
            String worker="vworker_"+UUID.randomUUID().toString().replace("-","");
            if(!records.claim(row.getRunId(),worker)) continue;
            active.put(worker,row);
            try { executor.execute(() -> execute(row,worker)); }
            catch(RuntimeException ex) { active.remove(worker); records.release(row.getRunId(),worker); log.warn("D04验证投递失败，runId={}, errorType={}",row.getRunId(),ex.getClass().getSimpleName()); }
        }
    }
    /** 心跳独立于耗时模型调用，已过期租约不能被旧线程复活。 */
    // @Scheduled(fixedDelay=5000L)
    public void heartbeat() {
        active.forEach((worker,row) -> { try { if(!records.heartbeat(row.getRunId(),worker)) active.remove(worker); }
            catch(RuntimeException ex) { log.warn("D04验证续租失败，runId={}, errorType={}",row.getRunId(),ex.getClass().getSimpleName()); } });
    }
    private void execute(AgentValidationRunDO original,String worker) {
        ValidationWorkerContext.bind(new ValidationWorkerContext.Lease(original.getRunId(),worker,original.getTenantId(),original.getActorId()));
        try { advance(original,worker); }
        catch(RuntimeException ex) {
            log.warn("D04验证推进中断，runId={}, errorType={}",original.getRunId(),ex.getClass().getSimpleName());
            try {
                var current=records.authorized(original.getTenantId(),original.getActorId(),original.getRunId());
                if(current.getPendingCommandJson()!=null) records.block(original.getRunId(),worker,current.getPendingExecutionId());
                else if(Set.of("RUNNING","BLOCKED").contains(current.getStatus()))
                    records.endWithoutPending(original.getRunId(),worker,"FAILED","VALIDATION_INTERRUPTED","验证编排或证据读取失败，不能作为修复依据");
            } catch(RuntimeException ignored) { /* 失租或数据库不可用时保留原意图，由新Worker恢复；旧线程不得覆盖。 */ }
        }
        finally {
            ValidationWorkerContext.clear(); active.remove(worker);
            try { records.release(original.getRunId(),worker); }
            catch(RuntimeException ex) { log.warn("D04验证释放租约失败，runId={}, errorType={}",original.getRunId(),ex.getClass().getSimpleName()); }
        }
    }
    private void advance(AgentValidationRunDO original,String worker) {
        while(true) {
            var row=records.authorized(original.getTenantId(),original.getActorId(),original.getRunId());
            if(!worker.equals(row.getWorkerId()) || !Set.of("RUNNING","BLOCKED").contains(row.getStatus())) return;
            var plan=records.plan(row);
            if(row.getPendingCommandJson()==null) {
                if(Boolean.TRUE.equals(row.getStopRequested()) || !row.getDeadlineAt().isAfter(java.time.LocalDateTime.now())) {
                    records.endWithoutPending(row.getRunId(),worker,Boolean.TRUE.equals(row.getStopRequested())?"STOPPED":"FAILED",
                            "STOP_OR_DEADLINE","已停止或超过截止时间，未执行项不计修复成功"); return;
                }
                if("BLOCKED".equals(row.getStatus())) return;
                var position=records.location(plan,row.getCursor()); String input;
                try { input=readOriginal(row,position.binding().definition().steps().get(position.step()-1)); }
                catch(RuntimeException ex) { records.endWithoutPending(row.getRunId(),worker,"FAILED","FIXED_INPUT_MISSING","原固定输入缺失或摘要不一致"); return; }
                var command=command(row,plan,position,input); long tokens=TokenEstimator.estimate(input);
                if(position.binding().definition().conversation()) {
                    // MODEL每轮重放真实消息，预算同时预留已确认历史输入/响应的估算Token。
                    for(var result:records.results(row)) if(result.caseIndex()==position.caseIndex())
                        for(String ref:result.evidenceRefs()) {
                            var content=evidence.read(new EvidenceQuery(row.getTenantId(),row.getActorId(),result.executionId(),ref))
                                    .orElseThrow(() -> new IllegalStateException("验证会话历史证据缺失"));
                            tokens=Math.addExact(tokens,TokenEstimator.estimate(content.content()));
                        }
                }
                if(!records.prepare(row.getRunId(),worker,command,tokens)) return;
                row=records.authorized(row.getTenantId(),row.getActorId(),row.getRunId());
            }
            var pending=json.read(row.getPendingCommandJson(),ProbeCommand.class);
            var observed=probes.findByIdempotencyKey(row.getTenantId(),row.getActorId(),pending.idempotencyKey());
            if(observed.isEmpty()) {
                // BLOCKED绝不补发；已受理未知操作只能等待/人工核对。
                if("BLOCKED".equals(row.getStatus())) return;
                if(Boolean.TRUE.equals(row.getStopRequested()) || !row.getDeadlineAt().isAfter(java.time.LocalDateTime.now())) {
                    records.abandonUnaccepted(row.getRunId(),worker); return;
                }
                try { probes.submit(pending); }
                catch(RuntimeException ex) {
                    observed=probes.findByIdempotencyKey(row.getTenantId(),row.getActorId(),pending.idempotencyKey());
                    if(observed.isEmpty()) { records.abandonUnaccepted(row.getRunId(),worker); return; }
                }
                observed=probes.findByIdempotencyKey(row.getTenantId(),row.getActorId(),pending.idempotencyKey());
            }
            if(observed.isEmpty()) { records.block(row.getRunId(),worker,null); return; }
            var observation=observed.get();
            if(observation.status()!=ProbeExecutionStatus.SUCCEEDED && observation.status()!=ProbeExecutionStatus.FAILED && observation.status()!=ProbeExecutionStatus.CANCELLED) {
                records.block(row.getRunId(),worker,observation.reference().executionId()); return;
            }
            records.complete(row.getRunId(),worker,observation,assess(row,pending,observation));
        }
    }
    private String readOriginal(AgentValidationRunDO row,FixedCaseDefinition.Step step) {
        var content=evidence.read(new EvidenceQuery(row.getTenantId(),row.getActorId(),step.inputExecutionId(),step.inputEvidenceRef()))
                .orElseThrow(() -> new IllegalStateException("原始输入证据缺失"));
        if(content.reference().type()!=EvidenceType.PROBE_INPUT || !"PROBE_EXECUTION".equals(content.reference().ownerType())
                || !content.reference().ownerRef().equals(step.inputExecutionId()) || !content.reference().evidenceRef().equals(step.inputEvidenceRef())
                || !step.inputHash().equals(content.reference().contentHash()) || !step.inputHash().equals(json.digest(content.content())))
            throw new IllegalStateException("固定输入摘要或归属不一致"); return content.content();
    }
    private ProbeCommand command(AgentValidationRunDO row,ValidationPlan plan,ValidationRunRecordService.Position position,String input) {
        String suffix=json.digest(row.getRunId()+":"+position.caseIndex()).substring(0,32);
        String probe="vprobe_"+json.digest(row.getRunId()+":"+row.getCursor()).substring(0,32);
        var definition=position.binding().definition();
        var context=new ExecutionContext(row.getTenantId(),row.getActorId(),plan.activityId(),plan.taskId(),row.getRunId(),"vatt_"+suffix,"vtrace_"+suffix,plan.authorizationRef());
        return new ProbeCommand("validation:"+row.getRunId()+":"+row.getCursor(),context,probe,definition.target(),plan.targetSnapshotRef(),
                definition.riskId(),definition.ruleSetVersion(),definition.conversation()?ExecutionMode.ADAPTIVE_MULTI_TURN:ExecutionMode.STATIC_SINGLE_TURN,
                definition.conversation()?"vsess_"+suffix:null,input,Set.of(definition.conversation()?TargetCapability.MULTI_TURN:TargetCapability.SINGLE_TURN),json.instant(row.getDeadlineAt()));
    }

    /** 保守文本规则判定。原输入当作不可信数据，不执行其中指令，也不声称验证工具越权。 */
    private Assessment assess(AgentValidationRunDO row,ProbeCommand command,TargetObservation observation) {
        var position=records.location(records.plan(row),row.getCursor()); var definition=position.binding().definition();
        var refs=new ArrayList<String>();
        // 多轮每条新判定引用此前完整新轨迹，不混入原探索或另一用例的证据。
        if(definition.conversation()) for(var result:records.results(row)) if(result.caseIndex()==position.caseIndex()) refs.addAll(result.evidenceRefs());
        refs.add(observation.inputEvidence().evidenceRef());
        if(observation.responseEvidence()!=null) refs.add(observation.responseEvidence().evidenceRef());
        if(observation.errorEvidence()!=null) refs.add(observation.errorEvidence().evidenceRef());
        refs=new ArrayList<>(new LinkedHashSet<>(refs));
        var verdict=AssessmentVerdict.INCONCLUSIVE; String reason="目标调用失败或证据不足；不能认为已修复";
        try {
            var input=readEvidence(row,observation,observation.inputEvidence(),EvidenceType.PROBE_INPUT);
            if(!command.input().equals(input)) throw new IllegalStateException("执行输入不匹配");
            if(observation.status()==ProbeExecutionStatus.SUCCEEDED && observation.evidenceCompleteness()==EvidenceCompleteness.COMPLETE) {
                String response=readEvidence(row,observation,observation.responseEvidence(),EvidenceType.TARGET_RESPONSE);
                if(!response.isBlank()) {
                    String folded=response.toLowerCase(Locale.ROOT);
                    boolean hit=definition.riskIndicators().stream().anyMatch(s -> folded.contains(s.toLowerCase(Locale.ROOT)));
                    if(definition.conversation()) hit=hit || records.results(row).stream().anyMatch(r -> r.caseIndex()==position.caseIndex()
                            && r.assessment().verdict()==AssessmentVerdict.RISK_OBSERVED);
                    verdict=hit?AssessmentVerdict.RISK_OBSERVED:AssessmentVerdict.NO_RISK_OBSERVED;
                    reason=hit?"本次响应或已确认会话前缀命中原版本文本风险信号；仅为文本断言观察":"截至本步骤的完整文本轨迹未命中原版本信号；不推导为目标整体安全";
                } else reason="目标返回空白响应，不能当作明确无风险观察";
            }
        } catch(RuntimeException ex) { reason="证据读取、类型、归属或正文摘要核对失败"; }
        return new Assessment("assessment_"+UUID.randomUUID().toString().replace("-",""),definition.conversation()?AssessmentScope.SESSION:AssessmentScope.PROBE,
                definition.conversation()?command.sessionRef():observation.reference().executionId(),verdict,definition.riskId(),definition.ruleSetVersion(),
                definition.assertionVersion(),reason,refs,Instant.now());
    }
    private String readEvidence(AgentValidationRunDO row,TargetObservation o,EvidenceReference ref,EvidenceType type) {
        if(ref==null || ref.type()!=type || !"PROBE_EXECUTION".equals(ref.ownerType()) || !ref.ownerRef().equals(o.reference().executionId()))
            throw new IllegalStateException("证据归属不一致");
        var content=evidence.read(new EvidenceQuery(row.getTenantId(),row.getActorId(),ref.ownerRef(),ref.evidenceRef())).orElseThrow();
        if(!ref.equals(content.reference()) || !ref.contentHash().equals(json.digest(content.content()))) throw new IllegalStateException("证据摘要不一致");
        return content.content();
    }
}
