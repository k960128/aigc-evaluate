package com.kant.agent.acceptance;

import com.kant.agent.contract.redteam.AttackSessionStatus;
import com.kant.agent.contract.redteam.SessionCheckpoint;
import com.kant.agent.contract.target.TargetSessionMessage;
import com.kant.agent.contract.target.TargetSessionMessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** D03消息/检查点契约测试；真实消息重放、命令竞争、租约和SSE还需集成验收。 */
class AgentSessionContractTest {
    @Test
    @DisplayName("多轮用户消息保留原文，助手空回复也保留为事实")
    void preservesConfirmedMessages() {
        String input = "  记住虚构代号 BLUE\n";
        assertEquals(input, new TargetSessionMessage(1, TargetSessionMessageRole.USER, input).content());
        assertEquals("", new TargetSessionMessage(2, TargetSessionMessageRole.ASSISTANT, "").content());
    }

    @Test
    @DisplayName("拒绝空白用户消息和无效消息序号")
    void rejectsInvalidMessages() {
        assertThrows(IllegalArgumentException.class,
                () -> new TargetSessionMessage(1, TargetSessionMessageRole.USER, " \n "));
        assertThrows(IllegalArgumentException.class,
                () -> new TargetSessionMessage(0, TargetSessionMessageRole.ASSISTANT, "虚构响应"));
    }

    @Test
    @DisplayName("检查点保存原会话引用、推进序号和历史摘要")
    void keepsCheckpointReferences() {
        var checkpoint = checkpoint(4, 2, 3);
        assertEquals("session-1", checkpoint.sessionId());
        assertEquals(4L, checkpoint.version());
        assertEquals(2, checkpoint.turnCount());
        assertEquals(3, checkpoint.nextSequence());
        assertEquals("history-digest-test", checkpoint.historyHash());
        assertEquals(AttackSessionStatus.PAUSED, checkpoint.status());
    }

    @Test
    @DisplayName("检查点不接受负版本、负轮次或零动作序号")
    void rejectsInvalidCheckpointCounters() {
        assertThrows(IllegalArgumentException.class, () -> checkpoint(-1, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> checkpoint(4, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> checkpoint(4, 2, 0));
    }

    private SessionCheckpoint checkpoint(long version, int turns, int nextSequence) {
        return new SessionCheckpoint("checkpoint-1", "session-1", "task-1", "run-1", "attempt-1",
                version, turns, nextSequence, "probe-2", "execution-2", "history-digest-test",
                AttackSessionStatus.PAUSED, Instant.parse("2026-10-01T00:00:00Z"));
    }
}
