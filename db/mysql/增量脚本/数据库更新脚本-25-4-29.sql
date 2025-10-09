DROP TABLE IF EXISTS `report_info`;
CREATE TABLE `report_info` (
                               `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                               `task_id` bigint(20) NOT NULL COMMENT '任务ID',
                               `model_id` bigint(20) NOT NULL COMMENT '模型ID',
                               `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名称',
                               `file_path` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件路径',
                               `desc` text COLLATE utf8mb4_unicode_ci COMMENT '描述(备注)',
                               `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
                               `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
                               `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
                               PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告信息表';