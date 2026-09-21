package com.kant.agent.contract.evaluation;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 一次规划使用的评测资源快照。
 *
 * <p>由后端读取现有模型、风险分类和数据集信息后构建，
 * 作为主智能体制定计划的事实依据。</p>
 *
 * <p>本对象只携带规划需要的信息，
 * 不包含模型 API Key、数据库连接信息或完整评测结果。</p>
 *
 * <p>快照代表获取时的资源情况。
 * 后续正式提交评测任务前，仍需重新检查权限和资源是否发生变化。</p>
 *
 * @param snapshotId      资源快照唯一标识，由后端生成
 * @param capturedAt      获取资源快照的时间
 * @param targetModelId   被测模型 ID
 * @param targetModelName 被测模型名称
 * @param risks           本次规划可参考的风险小类信息
 * @param datasets        本次规划可选择的数据集信息
 */
public record EvaluationResources(
        String snapshotId,
        Instant capturedAt,
        Long targetModelId,
        String targetModelName,
        List<RiskResource> risks,
        List<DatasetResource> datasets
) {

    public EvaluationResources {
        snapshotId = requireText(snapshotId, "snapshotId");
        targetModelName = requireText(
                targetModelName,
                "targetModelName"
        );

        requirePositiveId(targetModelId, "targetModelId");

        if (capturedAt == null) {
            throw new IllegalArgumentException("capturedAt 不能为空");
        }

        if (risks == null || datasets == null) {
            throw new IllegalArgumentException(
                    "risks 和 datasets 不能为 null，可以为空列表"
            );
        }

        // 保存不可变副本，同时拒绝列表中的 null 元素。
        risks = List.copyOf(risks);
        datasets = List.copyOf(datasets);

        // 同一份快照内，一个风险小类只能出现一次。
        Set<Long> riskIds = new HashSet<>();
        for (RiskResource risk : risks) {
            if (!riskIds.add(risk.riskDetailsId())) {
                throw new IllegalArgumentException(
                        "存在重复风险小类：" + risk.riskDetailsId()
                );
            }
        }

        // 同一份快照内，一个数据集只能出现一次。
        Set<Long> datasetIds = new HashSet<>();
        for (DatasetResource dataset : datasets) {
            if (!datasetIds.add(dataset.datasetId())) {
                throw new IllegalArgumentException(
                        "存在重复数据集：" + dataset.datasetId()
                );
            }
        }
    }

    /**
     * 供主智能体理解的风险小类信息。
     *
     * @param riskDetailsId 风险小类 ID，引用现有风险分类
     * @param name          风险小类名称
     * @param description   风险定义或说明；未提供时保存为空字符串
     */
    public record RiskResource(
            Long riskDetailsId,
            String name,
            String description
    ) {

        public RiskResource {
            requirePositiveId(riskDetailsId, "riskDetailsId");
            name = requireText(name, "name");
            description = description == null
                    ? ""
                    : description.strip();
        }
    }

    /**
     * 供主智能体选择的数据集信息。
     *
     * <p>数量和风险分类必须来自现有数据集的真实内容，
     * 不能使用模型估算值。</p>
     *
     * <p>riskDetailsIds 必须保留该数据集实际包含的全部风险小类，
     * 不能为了匹配用户授权范围而删掉范围之外的类别。</p>
     *
     * @param datasetId               数据集 ID
     * @param name                    数据集名称
     * @param sampleCount             数据集中可执行样本的总数，允许为 0
     * @param riskDetailsIds          样本实际涉及的风险小类 ID 集合
     * @param unclassifiedSampleCount 无法确定风险分类的样本数量
     * @param contentFingerprint      数据集内容指纹，由后端确定性计算；
     *                                用于识别样本内容、成员或风险归属的变化
     */
    public record DatasetResource(
            Long datasetId,
            String name,
            int sampleCount,
            Set<Long> riskDetailsIds,
            int unclassifiedSampleCount,
            String contentFingerprint
    ) {

        public DatasetResource {
            requirePositiveId(datasetId, "datasetId");
            name = requireText(name, "name");
            contentFingerprint = requireText(
                    contentFingerprint,
                    "contentFingerprint"
            );

            if (sampleCount < 0) {
                throw new IllegalArgumentException(
                        "sampleCount 不能小于 0"
                );
            }

            if (unclassifiedSampleCount < 0
                    || unclassifiedSampleCount > sampleCount) {
                throw new IllegalArgumentException(
                        "unclassifiedSampleCount 必须在 0 到 sampleCount 之间"
                );
            }

            if (riskDetailsIds == null) {
                throw new IllegalArgumentException(
                        "riskDetailsIds 不能为 null，可以为空集合"
                );
            }

            for (Long riskId : riskDetailsIds) {
                requirePositiveId(riskId, "riskDetailsIds 中的 ID");
            }

            riskDetailsIds = Set.copyOf(riskDetailsIds);
        }
    }

    /**
     * 校验引用现有业务资源的 ID。
     */
    private static void requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " 必须为正整数"
            );
        }
    }

    /**
     * 校验必填文本，并移除首尾空白。
     */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " 不能为空"
            );
        }

        return value.strip();
    }
}