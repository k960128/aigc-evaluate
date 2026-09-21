package com.kant.agent.orchestration;

import com.kant.agent.contract.evaluation.EvaluationIntent;
import com.kant.agent.contract.evaluation.EvaluationResources;

import java.util.ArrayList;
import java.util.List;

/**
 * 评测意图与资源快照的一致性校验器。
 *
 * <p>输入应是后端已经查询过权限的资源事实。
 * 本类不能代替真实的权限查询。</p>
 */
public final class EvaluationIntentValidator {

    public ValidationResult validate(
            EvaluationIntent intent,
            EvaluationResources resources
    ) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (intent == null) {
            issues.add(new ValidationIssue(
                    "intent", "INTENT_REQUIRED", "评测意图不能为空"
            ));
        }
        if (resources == null) {
            issues.add(new ValidationIssue(
                    "resources", "RESOURCES_REQUIRED", "资源快照不能为空"
            ));
        }
        if (!issues.isEmpty()) {
            return new ValidationResult(issues);
        }

        if (!intent.targetModelId().equals(resources.targetModelId())) {
            issues.add(new ValidationIssue(
                    "targetModelId",
                    "MODEL_MISMATCH",
                    "资源快照中的模型与评测目标不一致"
            ));
        }

        if (resources.risks().isEmpty()) {
            issues.add(new ValidationIssue(
                    "resources.risks",
                    "NO_AVAILABLE_RISKS",
                    "没有可用于本次规划的风险分类"
            ));
        }

        if (resources.datasets().isEmpty()) {
            issues.add(new ValidationIssue(
                    "resources.datasets",
                    "NO_AVAILABLE_DATASETS",
                    "没有可用于本次规划的数据集"
            ));
        }

        for (var risk : resources.risks()) {
            if (!intent.allowedRiskDetailsIds().contains(
                    risk.riskDetailsId())) {
                issues.add(new ValidationIssue(
                        "resources.risks",
                        "RESOURCE_RISK_OUT_OF_SCOPE",
                        "资源快照包含授权范围之外的风险分类"
                ));
            }
        }

        for (var dataset : resources.datasets()) {
            if (!intent.allowedDatasetIds().contains(dataset.datasetId())) {
                issues.add(new ValidationIssue(
                        "resources.datasets",
                        "RESOURCE_DATASET_OUT_OF_SCOPE",
                        "资源快照包含未授权的数据集"
                ));
            }
        }

        return new ValidationResult(issues);
    }
}