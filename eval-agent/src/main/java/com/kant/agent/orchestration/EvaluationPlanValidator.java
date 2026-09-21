package com.kant.agent.orchestration;

import com.kant.agent.contract.evaluation.EvaluationIntent;
import com.kant.agent.contract.evaluation.EvaluationPlan;
import com.kant.agent.contract.evaluation.EvaluationPlanDraft;
import com.kant.agent.contract.evaluation.EvaluationResources;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评测计划校验器。
 *
 * <p>只依据传入的意图、草案和资源事实进行确定性判断。</p>
 */
public final class EvaluationPlanValidator {

    private final EvaluationIntentValidator intentValidator =
            new EvaluationIntentValidator();

    /**
     * 校验模型生成的计划草案。
     */
    public ValidationResult validate(
            EvaluationIntent intent,
            EvaluationResources resources,
            EvaluationPlanDraft draft
    ) {
        List<ValidationIssue> issues = new ArrayList<>(
                intentValidator.validate(intent, resources).issues()
        );

        if (draft == null) {
            issues.add(new ValidationIssue(
                    "draft", "DRAFT_REQUIRED", "计划草案不能为空"
            ));
        }

        if (intent == null || resources == null || draft == null) {
            return new ValidationResult(issues);
        }

        if (!intent.targetModelId().equals(draft.targetModelId())) {
            issues.add(new ValidationIssue(
                    "draft.targetModelId",
                    "PLAN_MODEL_MISMATCH",
                    "计划不能更换本次授权的目标模型"
            ));
        }

        if (!intent.allowedDatasetIds().contains(draft.datasetId())) {
            issues.add(new ValidationIssue(
                    "draft.datasetId",
                    "UNAUTHORIZED_DATASET",
                    "计划选择了未授权的数据集"
            ));
        }

        if (!intent.allowedRiskDetailsIds().containsAll(
                draft.riskDetailsIds())) {
            issues.add(new ValidationIssue(
                    "draft.riskDetailsIds",
                    "UNAUTHORIZED_RISK",
                    "计划包含授权范围之外的风险分类"
            ));
        }

        Set<Long> knownRisks = resources.risks().stream()
                .map(EvaluationResources.RiskResource::riskDetailsId)
                .collect(Collectors.toSet());

        if (!knownRisks.containsAll(draft.riskDetailsIds())) {
            issues.add(new ValidationIssue(
                    "draft.riskDetailsIds",
                    "UNKNOWN_RISK",
                    "计划引用了资源快照中不存在的风险分类"
            ));
        }

        var selected = resources.datasets().stream()
                .filter(dataset -> dataset.datasetId().equals(
                        draft.datasetId()))
                .findFirst();

        if (selected.isEmpty()) {
            issues.add(new ValidationIssue(
                    "draft.datasetId",
                    "DATASET_NOT_AVAILABLE",
                    "所选数据集不在当前可用资源中"
            ));
            return new ValidationResult(issues);
        }

        var dataset = selected.get();

        if (dataset.sampleCount() == 0) {
            issues.add(new ValidationIssue(
                    "dataset.sampleCount",
                    "EMPTY_DATASET",
                    "所选数据集没有可执行样本"
            ));
        }

        if (dataset.unclassifiedSampleCount() > 0) {
            issues.add(new ValidationIssue(
                    "dataset.unclassifiedSampleCount",
                    "CLASSIFICATION_INCOMPLETE",
                    "所选数据集存在无法确定风险分类的样本"
            ));
        }

        if (!intent.allowedRiskDetailsIds().containsAll(
                dataset.riskDetailsIds())) {
            issues.add(new ValidationIssue(
                    "dataset.riskDetailsIds",
                    "DATASET_RISK_OUT_OF_SCOPE",
                    "数据集实际包含授权范围之外的风险分类"
            ));
        }

        if (!dataset.riskDetailsIds().equals(draft.riskDetailsIds())) {
            issues.add(new ValidationIssue(
                    "draft.riskDetailsIds",
                    "RISK_COVERAGE_MISMATCH",
                    "计划声明的风险范围与数据集实际范围不一致"
            ));
        }

        if (dataset.sampleCount()
                > intent.budget().maxEvaluationSamples()) {
            issues.add(new ValidationIssue(
                    "dataset.sampleCount",
                    "SAMPLE_BUDGET_EXCEEDED",
                    "数据集样本数超过本次样本预算"
            ));
        }

        if (dataset.sampleCount() > intent.budget().maxTargetCalls()) {
            issues.add(new ValidationIssue(
                    "dataset.sampleCount",
                    "TARGET_CALL_BUDGET_INSUFFICIENT",
                    "目标调用预算不足以执行全部样本"
            ));
        }

        return new ValidationResult(issues);
    }

    /**
     * 提交前，使用最新资源事实重新校验正式计划。
     *
     * <p>新的查询可以产生新的快照 ID，
     * 因此这里比较数据集内容指纹和数量，而不是要求快照 ID 相同。</p>
     */
    public ValidationResult validate(
            EvaluationIntent intent,
            EvaluationResources currentResources,
            EvaluationPlan plan
    ) {
        if (plan == null) {
            return ValidationResult.failure(
                    "plan", "PLAN_REQUIRED", "正式计划不能为空"
            );
        }

        List<ValidationIssue> issues = new ArrayList<>(
                validate(intent, currentResources, plan.draft()).issues()
        );

        if (intent == null || currentResources == null) {
            return new ValidationResult(issues);
        }

        // M1 直接采用意图中的预算，不允许修改预算对象。
        if (!plan.budget().equals(intent.budget())) {
            issues.add(new ValidationIssue(
                    "plan.budget",
                    "BUDGET_CHANGED",
                    "正式计划的预算与当前授权预算不一致"
            ));
        }

        currentResources.datasets().stream()
                .filter(dataset -> dataset.datasetId().equals(
                        plan.draft().datasetId()))
                .findFirst()
                .ifPresent(dataset -> {
                    if (!dataset.contentFingerprint().equals(
                            plan.datasetContentFingerprint())
                            || dataset.sampleCount()
                            != plan.plannedSampleCount()) {
                        issues.add(new ValidationIssue(
                                "plan.datasetContentFingerprint",
                                "DATASET_CHANGED",
                                "数据集内容或样本数量发生变化，需要重新规划和审批"
                        ));
                    }
                });

        return new ValidationResult(issues);
    }
}