# question_info_log表新增批次号字段
ALTER TABLE `question_info_log`
    ADD COLUMN `batch_no` BIGINT NULL COMMENT '批次号' AFTER `target_version`;