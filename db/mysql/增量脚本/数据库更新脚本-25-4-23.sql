# 题目信息表 新增字段 题目描述(备注)
ALTER TABLE `question_info`
    ADD COLUMN `desc` TEXT NULL COMMENT '描述(备注)' AFTER `version`;

# 题目信息流水表 新增字段 题目描述(备注)
ALTER TABLE `question_snapshot`
    ADD COLUMN `desc` TEXT NULL COMMENT '描述(备注)' AFTER `version`;