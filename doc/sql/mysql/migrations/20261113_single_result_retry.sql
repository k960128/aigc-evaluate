-- 上线前执行一次；执行期间暂停评测消费者，完成后统一升级模型及安全评测消费者。
-- 原消息未携带 executionVersion 时按 0 处理。
ALTER TABLE eval_result_detail
    ADD COLUMN execution_version INT NOT NULL DEFAULT 0 COMMENT '执行轮次',
    ADD COLUMN progress_counted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已计入批次完成数';

ALTER TABLE eval_pipeline_node_detail
    ADD COLUMN execution_version INT NOT NULL DEFAULT 0 COMMENT '样本执行轮次';

-- 旧链路的这些终态已计入完成数；STOPPED 未计入。
UPDATE eval_result_detail
SET progress_counted = CASE WHEN status IN (1, 2, 3, 8) THEN 1 ELSE 0 END;

CREATE TABLE eval_result_retry (
    id BIGINT NOT NULL PRIMARY KEY,
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_detail_id BIGINT NOT NULL,
    task_detail_id BIGINT NOT NULL,
    execution_version INT NOT NULL,
    mode VARCHAR(16) NOT NULL,
    start_stage VARCHAR(32) NOT NULL,
    create_time DATETIME NOT NULL,
    UNIQUE KEY uk_eval_result_retry_request (request_id),
    KEY idx_eval_result_retry_result_version (result_detail_id, execution_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单样本重试受理记录及事务消息回查凭据';
