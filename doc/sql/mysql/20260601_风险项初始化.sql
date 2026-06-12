CREATE TABLE `risk_category` (
                                 `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                 `category_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '大类名称 (如：核心价值观与政治敏感)',
                                 `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '大类描述/防范目标',
                                 `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序权重 (数值越小越靠前)',
                                 `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用, 1-启用',
                                 `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
                                 `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 UNIQUE KEY `uk_category_name` (`category_name`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全评测-风险大类表';


INSERT INTO `risk_category` (`id`, `category_name`, `description`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (1, '核心价值观与政治敏感', '重点合规保障，维护国家安全与政治稳定', 1, 1, 0, '2026-04-29 14:46:28', '2026-06-03 02:33:17');
INSERT INTO `risk_category` (`id`, `category_name`, `description`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (2, '违法犯罪与危险行为', '保障公共安全，防止教授或诱导犯罪', 2, 1, 0, '2026-04-29 14:46:28', '2026-06-03 02:33:18');
INSERT INTO `risk_category` (`id`, `category_name`, `description`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (3, '色情低俗与未保', '坚守道德底线，重点保护未成年人身心健康', 3, 1, 0, '2026-04-29 14:46:28', '2026-06-03 02:33:19');
INSERT INTO `risk_category` (`id`, `category_name`, `description`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (4, '歧视、仇恨与网暴', '维护健康网络环境，防止偏见与恶意攻击', 4, 1, 0, '2026-04-29 14:46:28', '2026-06-03 02:33:20');
INSERT INTO `risk_category` (`id`, `category_name`, `description`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (5, '隐私、安全与系统越权', '防范技术滥用，保护个人隐私与系统边界', 5, 1, 0, '2026-04-29 14:46:28', '2026-06-03 02:33:22');

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


INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (1, 1, '煽动颠覆/分裂国家', 101, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (2, 1, '明确的敏感政治事件/人物', 102, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (3, 1, '贬损/恶搞国家象征', 103, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (4, 1, '宣扬恐怖主义/极端主义', 104, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (5, 1, '时政新闻违规点评', 105, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (6, 1, '散布涉政谣言', 106, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (7, 2, '毒品制造与非法交易', 201, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (8, 2, '武器/爆炸物制作', 202, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (9, 2, '鼓励自残与自杀', 203, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (10, 2, '传授犯罪方法/反侦察', 204, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (11, 2, '网络诈骗与赌博', 205, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (12, 2, '危险化学品/生物制剂', 206, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (13, 2, '非法交易与走私黑产', 207, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (14, 3, '儿童色情 (CSAM)', 301, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (15, 3, '露骨色情与性行为描述', 302, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (16, 3, '招嫖与非法性交易', 303, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (17, 3, '针对未成年人的不良诱导', 304, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (18, 3, '软色情与擦边低俗内容', 305, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (19, 3, '违背公序良俗的性癖好', 306, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (20, 4, '极端恶意谩骂与人身攻击', 401, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (21, 4, '针对特定群体的仇恨言论', 402, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (22, 4, '基于种族/地域的歧视', 403, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (23, 4, '基于性别/性向的歧视', 404, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (24, 4, '职业与社会地位歧视', 405, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (25, 4, '网络暴力与教唆人肉', 406, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (26, 5, '窃取/泄露个人隐私 (PII)', 501, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (27, 5, '商业机密与国家机密泄露', 502, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (28, 5, '已知固定越狱模板 (Jailbreak)', 503, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (29, 5, '恶意代码生成与黑客工具', 504, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (30, 5, '提示词注入攻击 (Prompt Injection)', 505, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
INSERT INTO `risk_details` (`id`, `category_id`, `details_name`, `sort_order`, `status`, `deleted`, `create_time`, `update_time`) VALUES (31, 5, '动态越狱与角色扮演绕过', 506, 1, 0, '2026-04-29 14:46:28', '2026-04-29 14:46:28');
