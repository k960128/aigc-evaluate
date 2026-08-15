CREATE TABLE `risk_details` (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `category_id` bigint NOT NULL COMMENT '关联的风险大类ID (对应 risk_category.id)',
                                `details_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '具体风险项名称 (如：煽动颠覆/分裂国家)',
                                `sort_order` int NOT NULL DEFAULT '0' COMMENT '类目内排序权重',
                                `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用, 1-启用',
                                `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
                                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`id`),
                                KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全评测-31项风险明细项表';



CREATE TABLE `knowledge_vocabulary_keyword` (
                                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                                `group_id` bigint NOT NULL COMMENT '所属分组ID (关联 risk_vocabulary_group.id)',
                                                `risk_details_id` bigint NOT NULL COMMENT '关联31小类标签',
                                                `keyword` varchar(128) NOT NULL COMMENT '字面量特征词',
                                                `risk_level` tinyint NOT NULL DEFAULT '1' COMMENT '风险等级: 1-致命级别(命中即直接拦截), 2-疑似级别(仅打标签透传)',
                                                `match_type` tinyint NOT NULL DEFAULT '2' COMMENT '匹配模式: 1-精确匹配, 2-模糊包含匹配',
                                                `sync_status` tinyint NOT NULL DEFAULT '0' COMMENT 'Redis同步状态: 0-待同步, 1-已同步',
                                                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                `creator` varchar(64) DEFAULT NULL COMMENT '创建人',
                                                `updater` varchar(64) DEFAULT NULL COMMENT '更新人',
                                                `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识(0-未删除, 1-已删除)',
                                                PRIMARY KEY (`id`),
                                                UNIQUE KEY `uk_keyword` (`keyword`) COMMENT '保证全库特征词唯一',
                                                KEY `idx_group_id` (`group_id`) COMMENT '按分组快速拉取词条索引',
                                                KEY `idx_sync_status` (`sync_status`) COMMENT '快速拉取待同步数据的索引'
) ENGINE=InnoDB AUTO_INCREMENT=62 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库-风险词库特征词表';