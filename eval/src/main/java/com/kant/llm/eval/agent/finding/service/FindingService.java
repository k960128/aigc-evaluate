package com.kant.llm.eval.agent.finding.service;
import com.kant.agent.service.finding.FindingVerifier;
import com.kant.llm.eval.agent.finding.dto.*;
import com.kant.llm.eval.agent.probe.application.ProbePrincipalProvider;
import org.springframework.stereotype.Service;
import com.kant.framework.exception.ClientException;
import com.kant.llm.eval.agent.finding.error.AgentFindingErrorCode;
import java.util.List;
import java.util.function.Supplier;
/** D04应用入口：只处理查询和复核，不用“再读文本”伪装成目标复现。 */
@Service
public class FindingService {
    private final ProbePrincipalProvider principals;
    private final FindingRecordService records;
    private final FindingVerifier verifier;
    public FindingService(ProbePrincipalProvider principals,FindingRecordService records,FindingVerifier verifier) {
        this.principals=principals; this.records=records; this.verifier=verifier;
    }
    public FindingDetailResponse detail(String findingId) {
        var p=principals.currentPrincipal(); return guarded(() -> records.detail(p.tenantId(),p.actorId(),findingId));
    }
    public List<FindingReviewResponse> history(String findingId,long afterVersion) {
        var p=principals.currentPrincipal(); return guarded(() -> records.history(p.tenantId(),p.actorId(),findingId,afterVersion));
    }
    /** 一致性检查不更新确认结论，结果和完整证据索引另行留痕。 */
    public FindingReviewResponse checkEvidence(String findingId,FindingEvidenceReviewRequest request) {
        return guarded(() -> saveEvidenceReview(findingId,request));
    }
    private FindingReviewResponse saveEvidenceReview(String findingId,FindingEvidenceReviewRequest request) {
        var p=principals.currentPrincipal(); String hash=records.requestHash("EVIDENCE_CHECK",request);
        var replay=records.replay(p.tenantId(),p.actorId(),findingId,request.idempotencyKey(),hash);
        if (replay.isPresent()) return replay.get();
        var source=records.loadSource(p.tenantId(),p.actorId(),findingId);
        var report=verifier.inspect(p.tenantId(),p.actorId(),source.finding(),source.task(),source.actions());
        return records.append(p.tenantId(),p.actorId(),findingId,request.idempotencyKey(),request.expectedVersion(),hash,
                "EVIDENCE_CHECK",null,report);
    }
    /** 人工决策仍需服务端重做证据检查；模型和前端不能改写原判定。 */
    public FindingReviewResponse review(String findingId,FindingHumanReviewRequest request) {
        return guarded(() -> saveHumanReview(findingId,request));
    }
    private FindingReviewResponse saveHumanReview(String findingId,FindingHumanReviewRequest request) {
        var p=principals.currentPrincipal(); String hash=records.requestHash("HUMAN",request);
        var replay=records.replay(p.tenantId(),p.actorId(),findingId,request.idempotencyKey(),hash);
        if (replay.isPresent()) return replay.get();
        var source=records.loadSource(p.tenantId(),p.actorId(),findingId);
        var report=verifier.inspect(p.tenantId(),p.actorId(),source.finding(),source.task(),source.actions());
        return records.append(p.tenantId(),p.actorId(),findingId,request.idempotencyKey(),request.expectedVersion(),hash,"HUMAN",request,report);
    }
    /** 数据库/适配器异常不会把受限原文或SQL细节暴露给客户端。 */
    private <T> T guarded(Supplier<T> operation) {
        try { return operation.get(); }
        catch (ClientException ex) { throw ex; }
        catch (RuntimeException ex) { throw new ClientException("发现事实读取或复核保存失败",AgentFindingErrorCode.PERSISTENCE_ERROR); }
    }
}
