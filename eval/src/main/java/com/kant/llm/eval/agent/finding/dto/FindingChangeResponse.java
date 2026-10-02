package com.kant.llm.eval.agent.finding.dto;
import java.time.Instant;
/** changeId审计；findingId根发现；type命令；acceptedVersion首次受理后版本；remediationStatus当时整改状态；reason/actorRef/createdAt历史依据。 */
public record FindingChangeResponse(String changeId,String findingId,String type,long acceptedVersion,String remediationStatus,
        String reason,String actorRef,Instant createdAt,FindingLifecycleRequest request) {}
