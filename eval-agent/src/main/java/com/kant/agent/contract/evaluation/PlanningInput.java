package com.kant.agent.contract.evaluation;

/**
 * 主智能体一次规划所需的完整业务输入。
 *
 * <p>首次规划时，提供评测意图和资源快照。</p>
 *
 * <p>再次规划时，额外提供上一份可解析的草案和修订反馈。
 * 如果上一轮输出无法解析，可以不提供 previousDraft，
 * 但仍须提供明确的 revisionFeedback。</p>
 *
 * <p>attemptNumber 由后端编排器维护，模型不能修改。
 * 它表示计划生成尝试次数，与正式计划的版本号不是同一概念。</p>
 *
 * @param intent           用户评测意图，必填
 * @param resources        后端提供的资源快照，必填
 * @param attemptNumber    当前第几次尝试生成计划，从 1 开始
 * @param previousDraft    上一份可解析的计划草案；首次规划时必须为空
 * @param revisionFeedback 修订反馈；首次规划时为空，再次规划时必须提供
 */
public record PlanningInput(
        EvaluationIntent intent,
        EvaluationResources resources,
        int attemptNumber,
        EvaluationPlanDraft previousDraft,
        String revisionFeedback
) {

    public PlanningInput {
        if (intent == null) {
            throw new IllegalArgumentException("intent 不能为空");
        }

        if (resources == null) {
            throw new IllegalArgumentException("resources 不能为空");
        }

        if (attemptNumber <= 0) {
            throw new IllegalArgumentException(
                    "attemptNumber 必须从 1 开始"
            );
        }

        if (attemptNumber > intent.budget().maxPlanningAttempts()) {
            throw new IllegalArgumentException(
                    "当前规划次数超过 maxPlanningAttempts 上限"
            );
        }

        // 防止把其他被测模型的资源快照传给当前规划任务。
        if (!intent.targetModelId().equals(resources.targetModelId())) {
            throw new IllegalArgumentException(
                    "资源快照中的目标模型与评测意图不一致"
            );
        }

        revisionFeedback = revisionFeedback == null
                ? ""
                : revisionFeedback.strip();

        if (attemptNumber == 1) {
            if (previousDraft != null || !revisionFeedback.isEmpty()) {
                throw new IllegalArgumentException(
                        "首次规划不能携带上一份草案或修订反馈"
                );
            }
        } else if (revisionFeedback.isEmpty()) {
            throw new IllegalArgumentException(
                    "再次规划必须提供 revisionFeedback"
            );
        }
    }

    /**
     * 创建首次规划输入。
     *
     * @param intent    用户评测意图
     * @param resources 当前资源快照
     * @return 第一次规划的输入
     */
    public static PlanningInput initial(
            EvaluationIntent intent,
            EvaluationResources resources
    ) {
        return new PlanningInput(
                intent,
                resources,
                1,
                null,
                ""
        );
    }
}