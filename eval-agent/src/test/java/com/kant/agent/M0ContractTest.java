package com.kant.agent;

import com.kant.agent.contract.approval.ApprovalDecision;
import com.kant.agent.contract.approval.PlanApproval;
import com.kant.agent.contract.common.*;
import com.kant.agent.contract.evaluation.*;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M0 基础契约测试。
 */
class M0ContractTest {

    @Test
    void shouldRejectNonPositiveBudgets() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ExecutionBudget(0, 10, 10, 1000, 3)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ExecutionBudget(10, 0, 10, 1000, 3)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ExecutionBudget(10, 10, 0, 1000, 3)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ExecutionBudget(10, 10, 10, -1, 3)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ExecutionBudget(10, 10, 10, 1000, 0)
                )
        );
    }

    @Test
    void shouldDefensivelyCopyAuthorizedRiskIds() {
        Set<Long> original = new HashSet<>(Set.of(10L));

        var intent = new EvaluationIntent(
                "评测目标", 1L, original, Set.of(20L), M0Fixtures.budget()
        );

        original.add(99L);

        assertEquals(Set.of(10L), intent.allowedRiskDetailsIds());
        assertThrows(
                UnsupportedOperationException.class,
                () -> intent.allowedRiskDetailsIds().add(88L)
        );
    }

    @Test
    void shouldTreatDeadlineEqualityAsExpired() {
        var context = new AgentContext(
                "activity-1", "run-1", "trace-1",
                100L, M0Fixtures.NOW.plusSeconds(60)
        );

        assertFalse(context.isExpired(M0Fixtures.NOW));
        assertTrue(context.isExpired(M0Fixtures.NOW.plusSeconds(60)));
        assertTrue(context.isExpired(M0Fixtures.NOW.plusSeconds(61)));
    }

    @Test
    void shouldCreateInitialPlanningInput() {
        var input = PlanningInput.initial(
                M0Fixtures.intent(), M0Fixtures.resources()
        );

        assertEquals(1, input.attemptNumber());
        assertNull(input.previousDraft());
        assertEquals("", input.revisionFeedback());
    }

    @Test
    void shouldRequireFeedbackForAnotherPlanningAttempt() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlanningInput(
                        M0Fixtures.intent(),
                        M0Fixtures.resources(),
                        2,
                        M0Fixtures.draft(),
                        ""
                )
        );
    }

    @Test
    void shouldAllowRetryWhenPreviousOutputCouldNotBeParsed() {
        var input = new PlanningInput(
                M0Fixtures.intent(),
                M0Fixtures.resources(),
                2,
                null,
                "上一轮输出无法解析，请重新输出符合契约的计划"
        );

        assertNull(input.previousDraft());
        assertEquals(2, input.attemptNumber());
    }

    @Test
    void shouldRejectPlanningBeyondAttemptBudget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlanningInput(
                        M0Fixtures.intent(),
                        M0Fixtures.resources(),
                        4,
                        M0Fixtures.draft(),
                        "重新规划"
                )
        );
    }

    @Test
    void shouldPreserveUnknownTokenUsage() {
        var metadata = new AgentRunMetadata(
                "run-1", "planning-model", null, "prompt-v1",
                M0Fixtures.NOW, M0Fixtures.NOW.plusSeconds(1),
                null, null
        );

        assertNull(metadata.modelVersion());
        assertNull(metadata.inputTokens());
        assertNull(metadata.outputTokens());
    }

    @Test
    void shouldRejectInvalidRunMetadata() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AgentRunMetadata(
                        "run-1", "model", null, "prompt-v1",
                        M0Fixtures.NOW,
                        M0Fixtures.NOW.minusSeconds(1),
                        10L, 10L
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new AgentRunMetadata(
                        "run-1", "model", null, "prompt-v1",
                        M0Fixtures.NOW,
                        M0Fixtures.NOW.plusSeconds(1),
                        -1L, 10L
                )
        );
    }

    @Test
    void shouldRequireCommentWhenRejectingPlan() {
        var plan = M0Fixtures.plan();

        assertThrows(
                IllegalArgumentException.class,
                () -> new PlanApproval(
                        "approval-1",
                        plan.activityId(),
                        plan.planId(),
                        plan.version(),
                        plan.resourceSnapshotId(),
                        plan.datasetContentFingerprint(),
                        ApprovalDecision.REJECT,
                        100L,
                        M0Fixtures.NOW.plusSeconds(10),
                        ""
                )
        );
    }

    @Test
    void shouldNotInterpretUnknownPipelineStatusAsCompleted() {
        var snapshot = M0Fixtures.execution(
                M0Fixtures.reference(), 999
        );

        assertEquals(
                EvaluationExecutionSnapshot.State.UNKNOWN,
                snapshot.state()
        );
    }
}