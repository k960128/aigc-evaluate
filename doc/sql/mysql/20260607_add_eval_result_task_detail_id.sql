-- 结果明细增加任务运行批次字段，避免同一个评测任务多次提交后结果混批。
ALTER TABLE eval_result_detail
    ADD COLUMN task_detail_id BIGINT NULL COMMENT '任务执行批次ID' AFTER task_id;

-- 按批次查询进度和结果明细。
CREATE INDEX idx_eval_result_detail_task_detail_id
    ON eval_result_detail (task_detail_id);

-- 拆分消费者幂等保护：同一批次下同一样本只允许生成一条结果明细。
CREATE UNIQUE INDEX uk_eval_result_detail_task_detail_sample
    ON eval_result_detail (task_detail_id, sample_id);
