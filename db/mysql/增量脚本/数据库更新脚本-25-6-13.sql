# 新增脚本字段
ALTER TABLE `report_info`
    ADD COLUMN `manual_desc` TEXT COLLATE utf8mb4_unicode_ci COMMENT '人工描述';


ALTER TABLE `job_info`
    MODIFY COLUMN `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称';