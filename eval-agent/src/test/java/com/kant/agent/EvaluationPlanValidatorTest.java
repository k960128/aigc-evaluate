package com.kant.agent;

import com.kant.agent.contract.common.ExecutionBudget;
import com.kant.agent.contract.evaluation.EvaluationIntent;
import com.kant.agent.contract.evaluation.EvaluationPlanDraft;
import com.kant.agent.orchestration.EvaluationPlanValidator;
import com.kant.agent.orchestration.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证计划无法越过资源范围和预算。
 */
class EvaluationPlanValidatorTest {

    private final EvaluationPlanValidator validator =
            new EvaluationPlanValidator();

    @Test
    void shouldAcceptValidDraft() {
        var result = validator.validate(
                M0Fixtures.intent(),
                M0Fixtures.resources(),
                M0Fixtures.draft()
        );

        assertTrue(result.passed(), result.issues().toString());
    }

    @Test
    void shouldRejectChangedTargetModel() {
        var draft = new EvaluationPlanDraft(
                2L, 20L, Set.of(10L), "目标", "依据"
        );

        assertHasCode(
                validator.validate(
                        M0Fixtures.intent(), M0Fixtures.resources(), draft
                ),
                "PLAN_MODEL_MISMATCH"
        );
    }

    @Test
    void shouldRejectUnauthorizedDataset() {
        var draft = new EvaluationPlanDraft(
                1L, 99L, Set.of(10L), "目标", "依据"
        );

        assertHasCode(
                validator.validate(
                        M0Fixtures.intent(), M0Fixtures.resources(), draft
                ),
                "UNAUTHORIZED_DATASET"
        );
    }

    @Test
    void shouldRejectUnauthorizedRisk() {
        var draft = new EvaluationPlanDraft(
                1L, 20L, Set.of(99L), "目标", "依据"
        );

        assertHasCode(
                validator.validate(
                        M0Fixtures.intent(), M0Fixtures.resources(), draft
                ),
                "UNAUTHORIZED_RISK"
        );
    }

    @Test
    void shouldRejectSamplesBeyondBudget() {
        var resources = M0Fixtures.resources(
                101, Set.of(10L), 0, "fingerprint-v1"
        );

        assertHasCode(
                validator.validate(
                        M0Fixtures.intent(), resources, M0Fixtures.draft()
                ),
                "SAMPLE_BUDGET_EXCEEDED"
        );
    }

    @Test
    void shouldRejectInsufficientTargetCallBudget() {
        var intent = new EvaluationIntent(
                "目标",
                1L,
                Set.of(10L),
                Set.of(20L),
                new ExecutionBudget(100, 5, 10, 20_000, 3)
        );

        assertHasCode(
                validator.validate(
                        intent, M0Fixtures.resources(), M0Fixtures.draft()
                ),
                "TARGET_CALL_BUDGET_INSUFFICIENT"
        );
    }

    @Test
    void shouldRejectUnclassifiedSamples() {
        var resources = M0Fixtures.resources(
                10, Set.of(10L), 1, "fingerprint-v1"
        );

        assertHasCode(
                validator.validate(
                        M0Fixtures.intent(), resources, M0Fixtures.draft()
                ),
                "CLASSIFICATION_INCOMPLETE"
        );
    }

    @Test
    void shouldDetectRisksHiddenByPlanDeclaration() {
        var resources = M0Fixtures.resources(
                10, Set.of(10L, 99L), 0, "fingerprint-v1"
        );

        var result = validator.validate(
                M0Fixtures.intent(), resources, M0Fixtures.draft()
        );

        assertHasCode(result, "DATASET_RISK_OUT_OF_SCOPE");
        assertHasCode(result, "RISK_COVERAGE_MISMATCH");
    }

    @Test
    void shouldRejectDatasetChangedAfterPlanning() {
        var changed = M0Fixtures.resources(
                10, Set.of(10L), 0, "fingerprint-v2"
        );

        assertHasCode(
                validator.validate(
                        M0Fixtures.intent(), changed, M0Fixtures.plan()
                ),
                "DATASET_CHANGED"
        );
    }

    @Test
    void shouldReturnIssuesForMissingInputs() {
        var result = validator.validate(
                null, null, (EvaluationPlanDraft) null
        );

        assertFalse(result.passed());
        assertHasCode(result, "INTENT_REQUIRED");
        assertHasCode(result, "RESOURCES_REQUIRED");
        assertHasCode(result, "DRAFT_REQUIRED");
    }

    private static void assertHasCode(
            ValidationResult result,
            String expectedCode
    ) {
        assertFalse(result.passed());

        assertTrue(
                result.issues().stream().anyMatch(
                        issue -> expectedCode.equals(issue.code())
                ),
                () -> "缺少错误码 " + expectedCode + "，实际：" + result.issues()
        );
    }
}