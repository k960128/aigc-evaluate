package com.kant.agent;

import com.kant.agent.contract.approval.ApprovalDecision;
import com.kant.agent.contract.evaluation.*;
import com.kant.agent.orchestration.*;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.kant.agent.contract.evaluation.EvaluationActivityStatus.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * M0 活动生命周期和提交约束测试。
 */
class EvaluationLifecycleTest {

    private final EvaluationActivityStateMachine stateMachine =
            new EvaluationActivityStateMachine();

    private final PlanApprovalValidator approvalValidator =
            new PlanApprovalValidator();

    private final EvaluationSubmissionFactory submissionFactory =
            new EvaluationSubmissionFactory();

    @Test
    void shouldAllowStartingFromDraft() {
        var activity = new EvaluationActivity(
                "activity-1",
                M0Fixtures.intent(),
                DRAFT,
                null,
                null,
                null
        );

        assertTrue(
                stateMachine.validateTransition(activity, PLANNING).passed()
        );
    }

    @Test
    void shouldRejectSubmittingWithoutApproval() {
        var activity = M0Fixtures.activity(
                READY_TO_SUBMIT, null, null
        );

        assertFalse(
                stateMachine.validateTransition(activity, SUBMITTING).passed()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> submissionFactory.create(
                        activity, M0Fixtures.resources()
                )
        );
    }

    @Test
    void shouldRejectApprovalForOldPlanVersion() {
        var result = approvalValidator.validate(
                M0Fixtures.plan(2),
                M0Fixtures.approval()
        );

        assertFalse(result.passed());
        assertTrue(result.issues().stream().anyMatch(
                issue -> issue.code().equals("APPROVAL_VERSION_MISMATCH")
        ));
    }

    @Test
    void shouldRejectRejectedPlan() {
        var rejected = M0Fixtures.approval(
                M0Fixtures.plan(), ApprovalDecision.REJECT
        );

        assertFalse(
                approvalValidator.validate(
                        M0Fixtures.plan(), rejected
                ).passed()
        );
    }

    @Test
    void shouldAllowRevisionOnlyAfterReturnDecision() {
        var returned = M0Fixtures.approval(
                M0Fixtures.plan(),
                ApprovalDecision.RETURN_FOR_REVISION
        );

        var activity = M0Fixtures.activity(
                WAITING_APPROVAL, returned, null
        );

        assertTrue(
                stateMachine.validateTransition(activity, PLANNING).passed()
        );

        var notReturned = M0Fixtures.activity(
                WAITING_APPROVAL, M0Fixtures.approval(), null
        );

        assertFalse(
                stateMachine.validateTransition(
                        notReturned, PLANNING
                ).passed()
        );
    }

    @Test
    void shouldRequireTaskReferenceBeforeWaitingForEvaluation() {
        var activity = M0Fixtures.activity(
                SUBMITTING, M0Fixtures.approval(), null
        );

        assertFalse(
                stateMachine.validateTransition(
                        activity, WAITING_EVALUATION
                ).passed()
        );
    }

    @Test
    void shouldRequireCompletedStateOfTheSameBatch() {
        var reference = M0Fixtures.reference();

        var activity = M0Fixtures.activity(
                WAITING_EVALUATION,
                M0Fixtures.approval(),
                reference
        );

        // 没有真实执行快照，不能认定完成。
        assertFalse(
                stateMachine.validateTransition(activity, COMPLETED).passed()
        );

        // 状态仍在运行，不能认定完成。
        assertFalse(
                stateMachine.validateTransition(
                        activity,
                        COMPLETED,
                        M0Fixtures.execution(reference, 3)
                ).passed()
        );

        // 同一个批次真实完成，允许结束活动。
        assertTrue(
                stateMachine.validateTransition(
                        activity,
                        COMPLETED,
                        M0Fixtures.execution(reference, 4)
                ).passed()
        );

        // 其他批次完成，不能结束当前活动。
        var anotherBatch = new EvaluationTaskReference(
                1000L, 2001L, 20L, 1L
        );

        assertFalse(
                stateMachine.validateTransition(
                        activity,
                        COMPLETED,
                        M0Fixtures.execution(anotherBatch, 4)
                ).passed()
        );
    }

    @Test
    void shouldRequireStoppedBatchBeforeMarkingActivityCancelled() {
        var reference = M0Fixtures.reference();

        var activity = M0Fixtures.activity(
                WAITING_EVALUATION,
                M0Fixtures.approval(),
                reference
        );

        assertFalse(
                stateMachine.validateTransition(
                        activity,
                        CANCELLED,
                        M0Fixtures.execution(reference, 3)
                ).passed()
        );

        assertTrue(
                stateMachine.validateTransition(
                        activity,
                        CANCELLED,
                        M0Fixtures.execution(reference, 6)
                ).passed()
        );
    }

    @Test
    void shouldNotRestartTerminalActivity() {
        var activity = M0Fixtures.activity(
                COMPLETED,
                M0Fixtures.approval(),
                M0Fixtures.reference()
        );

        assertFalse(
                stateMachine.validateTransition(activity, PLANNING).passed()
        );
    }

    @Test
    void shouldBuildStableSubmissionCommand() {
        var activity = M0Fixtures.activity(
                READY_TO_SUBMIT, M0Fixtures.approval(), null
        );

        var first = submissionFactory.create(
                activity, M0Fixtures.resources()
        );
        var second = submissionFactory.create(
                activity, M0Fixtures.resources()
        );

        assertEquals(first.idempotencyKey(), second.idempotencyKey());
        assertEquals("activity-1", first.activityId());
        assertEquals(1L, first.targetModelId().longValue());
        assertEquals(20L, first.datasetId().longValue());
        assertEquals(10, first.plannedSampleCount());
        assertEquals(120, first.maxTargetCalls());
        assertEquals(M0Fixtures.approval().approvalId(), first.approvalId());
    }

    @Test
    void shouldRejectSubmissionWhenDatasetChanged() {
        var activity = M0Fixtures.activity(
                READY_TO_SUBMIT, M0Fixtures.approval(), null
        );

        var changed = M0Fixtures.resources(
                10, Set.of(10L), 0, "fingerprint-v2"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> submissionFactory.create(activity, changed)
        );
    }

    @Test
    void shouldRejectCreatingAnotherSubmissionForLinkedActivity() {
        var activity = M0Fixtures.activity(
                READY_TO_SUBMIT,
                M0Fixtures.approval(),
                M0Fixtures.reference()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> submissionFactory.create(
                        activity, M0Fixtures.resources()
                )
        );
    }
}