package com.kant.llm.eval.agent.acceptance;

import com.kant.agent.contract.evidence.*;
import com.kant.agent.contract.probe.*;
import com.kant.agent.contract.target.*;
import com.kant.agent.port.evidence.EvidenceReadPort;
import com.kant.agent.port.probe.ProbeExecutionPort;
import com.kant.agent.port.target.TargetCapabilityPort;
import com.kant.framework.web.GlobalExceptionHandler;
import com.kant.llm.eval.agent.probe.application.ProbeApplicationService;
import com.kant.llm.eval.agent.probe.application.ProbePrincipal;
import com.kant.llm.eval.agent.probe.controller.AgentProbeController;
import com.kant.llm.eval.agent.probe.controller.AgentTargetController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * D01第一批HTTP契约测试：真实Controller、DTO校验、应用服务和异常处理器，端口为内存替身。
 * 不使用@SpringBootTest，不连接数据库/模型，也不验证Sa-Token过滤器和持久化权限。
 */
class AgentProbeHttpContractTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private final ProbeExecutionPort probes = mock(ProbeExecutionPort.class);
    private final EvidenceReadPort evidence = mock(EvidenceReadPort.class);
    private final TargetCapabilityPort targets = mock(TargetCapabilityPort.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var service = new ProbeApplicationService(() -> new ProbePrincipal("tenant-test", "actor-test"),
                probes, evidence, targets);
        mvc = MockMvcBuilders.standaloneSetup(new AgentProbeController(service), new AgentTargetController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("提交返回物理执行ID，并从服务端身份构造探测上下文")
    void submitsUsingServerPrincipal() throws Exception {
        when(probes.submit(any())).thenReturn(submission(ProbeExecutionStatus.SUCCEEDED));
        mvc.perform(post("/agent/probes").contentType(MediaType.APPLICATION_JSON).content(request("STATIC_SINGLE_TURN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.probeId").value("probe-test"))
                .andExpect(jsonPath("$.data.executionId").value("execution-test"))
                .andExpect(jsonPath("$.data.newlyAccepted").value(true));
        var command = ArgumentCaptor.forClass(ProbeCommand.class);
        verify(probes).submit(command.capture());
        assertEquals("tenant-test", command.getValue().context().tenantId());
        assertEquals("actor-test", command.getValue().context().actorId());
        assertEquals("  DEMO_INPUT\n", command.getValue().input());
        assertNull(command.getValue().sessionRef());
    }

    @Test
    @DisplayName("接口code成功仍可返回UNKNOWN，前端必须分开解释")
    void separatesHttpSuccessFromExecutionOutcome() throws Exception {
        when(probes.submit(any())).thenReturn(submission(ProbeExecutionStatus.UNKNOWN));
        mvc.perform(post("/agent/probes").contentType(MediaType.APPLICATION_JSON).content(request("STATIC_SINGLE_TURN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("UNKNOWN"));
    }

    @Test
    @DisplayName("缺少必填输入时DTO校验失败且不进入目标端口")
    void rejectsMissingInputBeforeExecution() throws Exception {
        String payload = request("STATIC_SINGLE_TURN").replace("\"input\":\"  DEMO_INPUT\\n\",", "");
        mvc.perform(post("/agent/probes").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not("0")));
        verifyNoInteractions(probes, evidence, targets);
    }

    @Test
    @DisplayName("无法解析的模式枚举是HTTP400，不应被当作普通成功响应")
    void rejectsUnknownEnum() throws Exception {
        mvc.perform(post("/agent/probes").contentType(MediaType.APPLICATION_JSON).content(request("NOT_A_MODE")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(probes, evidence, targets);
    }

    @Test
    @DisplayName("能力响应保持真实DTO字段，并将认证身份传入资源端口")
    void exposesCapabilityContract() throws Exception {
        var target = new TargetReference(TargetType.MODEL, "1");
        when(targets.getSnapshot("tenant-test", "actor-test", target)).thenReturn(new TargetSnapshot(
                "snapshot-test", target, "测试模型", "fictional-model", "config-v1", TargetStatus.AVAILABLE,
                "ISOLATED_TEST", "credential-reference-test", new TargetCapabilities(Set.of(TargetCapability.SINGLE_TURN)),
                NOW, List.of("配置能力声明，不是探活")));
        mvc.perform(get("/agent/targets/models/1/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetId").value("1"))
                .andExpect(jsonPath("$.data.targetSnapshotRef").value("snapshot-test"))
                .andExpect(jsonPath("$.data.supportedCapabilities[0]").value("SINGLE_TURN"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
        verify(targets).getSnapshot("tenant-test", "actor-test", target);
    }

    @Test
    @DisplayName("执行查询缺失返回业务错误，并保留服务端身份查询约束")
    void mapsMissingExecution() throws Exception {
        var query = new ProbeLookup("tenant-test", "actor-test", "execution-missing");
        when(probes.find(query)).thenReturn(Optional.empty());
        mvc.perform(get("/agent/probes/execution-missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A050004"));
        verify(probes).find(query);
    }

    @Test
    @DisplayName("证据返回原始文本和所属执行，正文是否安全渲染仍需浏览器验收")
    void returnsEvidenceAsData() throws Exception {
        var query = new EvidenceQuery("tenant-test", "actor-test", "execution-test", "response-test");
        var ref = new EvidenceReference("response-test", EvidenceType.TARGET_RESPONSE, "PROBE_EXECUTION",
                "execution-test", "text/plain; charset=UTF-8", "digest-test", EvidenceDataClass.RESTRICTED, NOW);
        when(evidence.read(query)).thenReturn(Optional.of(new EvidenceContent(ref, "<b>虚构文本，不执行HTML</b>")));
        mvc.perform(get("/agent/probes/execution-test/evidence/response-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reference.ownerRef").value("execution-test"))
                .andExpect(jsonPath("$.data.content").value("<b>虚构文本，不执行HTML</b>"));
        verify(evidence).read(query);
        verifyNoInteractions(probes, targets);
    }

    @Test
    @DisplayName("终态取消回执可仍是SUCCEEDED且未记录取消意图")
    void doesNotInventRemoteCancellation() throws Exception {
        var query = new ProbeLookup("tenant-test", "actor-test", "execution-test");
        when(probes.requestCancellation(query)).thenReturn(new ProbeCancellation(
                new ProbeExecutionReference("probe-test", "execution-test"), ProbeExecutionStatus.SUCCEEDED, false, null));
        mvc.perform(post("/agent/probes/execution-test/cancellation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.cancellationRequested").value(false));
        verify(probes).requestCancellation(query);
    }

    private ProbeSubmission submission(ProbeExecutionStatus status) {
        return new ProbeSubmission(new ProbeExecutionReference("probe-test", "execution-test"), status, true, NOW);
    }

    /** JSON仅含虚构机制数据；真实授权和目标必须在隔离环境另行确认。 */
    private String request(String mode) {
        return """
                {
                  "idempotencyKey":"key-test", "probeId":"probe-test",
                  "activityId":"activity-test", "taskId":"task-test",
                  "runId":"run-test", "attemptId":"attempt-test",
                  "authorizationRef":"approved-fictional-test", "targetModelId":1,
                  "expectedTargetSnapshotRef":"snapshot-test", "riskId":"fictional-risk",
                  "ruleSetVersion":"rules-v1", "executionMode":"%s",
                  "input":"  DEMO_INPUT\\n", "requiredCapabilities":["SINGLE_TURN"], "deadlineAt":null
                }
                """.formatted(mode);
    }
}
