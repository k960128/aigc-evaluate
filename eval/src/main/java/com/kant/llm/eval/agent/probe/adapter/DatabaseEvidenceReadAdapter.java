package com.kant.llm.eval.agent.probe.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.agent.contract.evidence.EvidenceContent;
import com.kant.agent.contract.evidence.EvidenceQuery;
import com.kant.agent.port.evidence.EvidenceReadPort;
import com.kant.llm.eval.agent.probe.dao.entity.AgentEvidenceDO;
import com.kant.llm.eval.agent.probe.dao.mapper.AgentEvidenceMapper;
import com.kant.llm.eval.agent.probe.service.AgentProbeRecordService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 基于受限证据表的证据读取适配器。
 *
 * <p>D01 同时校验租户、操作者、所属执行和证据引用，避免仅凭可猜测的引用
 * 跨执行读取原始提示或响应。后续接入角色权限时可在该端口继续扩展。</p>
 */
@Component
public class DatabaseEvidenceReadAdapter implements EvidenceReadPort {

    private final AgentEvidenceMapper evidenceMapper;
    private final AgentProbeRecordService recordService;

    public DatabaseEvidenceReadAdapter(AgentEvidenceMapper evidenceMapper,
                                       AgentProbeRecordService recordService) {
        this.evidenceMapper = evidenceMapper;
        this.recordService = recordService;
    }

    @Override
    public Optional<EvidenceContent> read(EvidenceQuery query) {
        AgentEvidenceDO evidence = evidenceMapper.selectOne(
                new LambdaQueryWrapper<AgentEvidenceDO>()
                        .eq(AgentEvidenceDO::getTenantId, query.tenantId())
                        .eq(AgentEvidenceDO::getActorId, query.actorId())
                        .eq(AgentEvidenceDO::getOwnerRef, query.ownerRef())
                        .eq(AgentEvidenceDO::getEvidenceRef, query.evidenceRef())
                        .last("LIMIT 1"));
        if (evidence == null) {
            return Optional.empty();
        }
        return Optional.of(new EvidenceContent(
                recordService.toEvidenceReference(evidence),
                evidence.getContent()));
    }
}
