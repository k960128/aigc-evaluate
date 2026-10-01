package com.kant.llm.eval.agent.finding.config;
import com.kant.agent.port.evidence.EvidenceReadPort;
import com.kant.agent.port.probe.ProbeExecutionPort;
import com.kant.agent.service.finding.FindingVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/** D04确定性领域服务装配，eval-agent不依赖Spring或持久化类型。 */
@Configuration
public class FindingConfiguration {
    @Bean
    public FindingVerifier findingVerifier(ProbeExecutionPort probes,EvidenceReadPort evidence) {
        return new FindingVerifier(probes,evidence);
    }
}
