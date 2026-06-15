-- 为数据集样本和评测结果明细补充题目绑定的风险小类。
-- L2 真实 ES + PGVector 召回会优先按该字段过滤知识库，避免跨风险小类误召回。

ALTER TABLE `dataset_sample`
    ADD COLUMN `risk_details_id` BIGINT DEFAULT NULL COMMENT '题目绑定的目标风险小类ID，对应 risk_details.id，用于L2按评测类型过滤召回'
        AFTER `question`;

CREATE INDEX `idx_dataset_sample_risk_details_id`
    ON `dataset_sample` (`risk_details_id`);

ALTER TABLE `eval_result_detail`
    ADD COLUMN `risk_details_id` BIGINT DEFAULT NULL COMMENT '题目绑定的目标风险小类ID，从dataset_sample冗余，用于L2召回过滤'
        AFTER `input_text`;

CREATE INDEX `idx_eval_result_detail_risk_details_id`
    ON `eval_result_detail` (`risk_details_id`);

-- 兼容已拆分但尚未执行或需要回放的历史结果明细。
-- 如果 dataset_sample 已经补齐 risk_details_id，可执行本语句把目标小类回填到结果明细。
UPDATE `eval_result_detail` erd
JOIN `dataset_sample` ds ON ds.id = erd.sample_id
SET erd.risk_details_id = ds.risk_details_id
WHERE erd.risk_details_id IS NULL
  AND ds.risk_details_id IS NOT NULL;
