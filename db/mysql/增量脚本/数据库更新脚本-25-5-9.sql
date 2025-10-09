# 新增脚本字段
ALTER TABLE `model_info`
    ADD COLUMN `use_script` bit(1) NULL DEFAULT b'0' COMMENT '是否使用脚本',
  ADD COLUMN `script_language` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '脚本语言',
  ADD COLUMN `script_source` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '脚本内容';
