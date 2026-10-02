-- 仅适用于已经执行过D04早期两表版本且下列五列均不存在的数据库。
-- 新库不要执行本脚本；非重复执行脚本，先人工核对information_schema.columns。
-- 本次开发没有连接数据库或执行任何SQL。
ALTER TABLE `agent_finding_state`
    ADD COLUMN `current_regression_run_id` VARCHAR(64) COLLATE utf8mb4_bin NULL COMMENT '当前指定固定回归',
    ADD COLUMN `repair_snapshot_ref` VARCHAR(160) COLLATE utf8mb4_bin NULL COMMENT '当前修复目标快照',
    ADD COLUMN `required_cases_json` LONGTEXT NULL COMMENT '当前全部正式用例固定版本',
    ADD COLUMN `resolved_by` VARCHAR(64) NULL COMMENT '确认修复认证负责人',
    ADD COLUMN `resolution_reason` VARCHAR(2000) NULL COMMENT '负责人确认修复依据';
