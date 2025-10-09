# 新增脚本字段
ALTER TABLE report_info
    ADD COLUMN reading_status BOOLEAN DEFAULT FALSE COMMENT '阅读状态',
    ADD COLUMN report_type VARCHAR(50) COMMENT '报告类型',
    ADD COLUMN user_id BIGINT COMMENT '用户ID';
