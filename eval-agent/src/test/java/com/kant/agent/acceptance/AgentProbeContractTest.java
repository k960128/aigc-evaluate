package com.kant.agent.acceptance;

import com.kant.agent.contract.common.ExecutionContext;
import com.kant.agent.contract.probe.ExecutionMode;
import com.kant.agent.contract.probe.ProbeCommand;
import com.kant.agent.contract.target.TargetCapabilities;
import com.kant.agent.contract.target.TargetCapability;
import com.kant.agent.contract.target.TargetReference;
import com.kant.agent.contract.target.TargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** D01第一批契约测试：不启动Spring、不访问数据库、不发送模型请求。 */
class AgentProbeContractTest {

    @Test
    @DisplayName("探测输入的首尾空白必须保留，不能改变证据正文")
    void preservesInputWhitespace() {
        String input = "  虚构机制检查\n";
        assertEquals(input, command(input, null, Set.of()).input());
    }

    @Test
    @DisplayName("静态单轮不能携带会话引用")
    void rejectsSessionForSingleTurn() {
        assertThrows(IllegalArgumentException.class,
                () -> command("虚构机制检查", "session-other", Set.of()));
    }

    @Test
    @DisplayName("空白输入和超长输入在契约层被拒绝")
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> command(" \n ", null, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> command("a".repeat(100_001), null, Set.of()));
    }

    @Test
    @DisplayName("能力集合冻结后不能被调用方修改")
    void freezesCapabilities() {
        var original = EnumSet.of(TargetCapability.SINGLE_TURN);
        ProbeCommand command = command("虚构机制检查", null, original);
        TargetCapabilities capabilities = new TargetCapabilities(original);
        original.add(TargetCapability.TOOL_TRACE);
        assertEquals(Set.of(TargetCapability.SINGLE_TURN), command.requiredCapabilities());
        assertFalse(capabilities.supports(TargetCapability.TOOL_TRACE));
        assertThrows(UnsupportedOperationException.class,
                () -> command.requiredCapabilities().add(TargetCapability.MULTI_TURN));
    }

    /** 测试引用仅用于内存契约，不表示已经存在授权记录或实际模型。 */
    private ProbeCommand command(String input, String session, Set<TargetCapability> capabilities) {
        var context = new ExecutionContext("tenant-test", "actor-test", "activity-test", "task-test",
                "run-test", "attempt-test", "trace-test", "approved-fictional-test");
        return new ProbeCommand("probe-key-test", context, "probe-test",
                new TargetReference(TargetType.MODEL, "1"), "snapshot-test", "fictional-risk",
                "rules-test-v1", ExecutionMode.STATIC_SINGLE_TURN, session, input, capabilities, null);
    }
}
