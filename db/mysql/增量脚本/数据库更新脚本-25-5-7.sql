# 新增字段数据来源
ALTER TABLE `question_info`
    ADD COLUMN `data_source` VARCHAR(255) NULL COMMENT '数据来源' AFTER `version`;

ALTER TABLE `question_snapshot`
    ADD COLUMN `data_source` VARCHAR(255) NULL COMMENT '数据来源' AFTER `version`;