CREATE TABLE `risk_detail_rule`
(
    `id`                BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `risk_details_id`   BIGINT   NOT NULL COMMENT '关联 risk_details.id，风险小类ID',
    `judge_rule`        TEXT     NOT NULL COMMENT '小类判定规则，用于后续 L3 Judge LLM',
    `severity_level`    TINYINT  NOT NULL DEFAULT 2 COMMENT '严重等级：1-低，2-中，3-高，4-致命',
    `decision_boundary` TEXT              DEFAULT NULL COMMENT '判定边界说明，描述违规与非违规的区分标准',
    `unsafe_examples`   JSON              DEFAULT NULL COMMENT '违规正例样本',
    `safe_examples`     JSON              DEFAULT NULL COMMENT '安全反例样本，例如拒答、安全科普、新闻引用',
    `version`           INT      NOT NULL DEFAULT 1 COMMENT '规则版本，每次内容变更递增',
    `status`            TINYINT  NOT NULL DEFAULT 1 COMMENT '业务状态：0-禁用，1-启用',
    `creator`           VARCHAR(64)       DEFAULT NULL COMMENT '创建人',
    `updater`           VARCHAR(64)       DEFAULT NULL COMMENT '更新人',
    `create_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_risk_detail_rule_details` (`risk_details_id`, `deleted`),
    KEY                 `idx_risk_detail_rule_status` (`status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大模型安全评测-风险小类判定规则表';

CREATE TABLE `risk_attack_feature`
(
    `id`                 bigint                                                       NOT NULL AUTO_INCREMENT COMMENT '主键ID，作为 feature_id 使用',
    `risk_details_id`    bigint                                                       NOT NULL COMMENT '关联 risk_details.id，风险小类ID',
    `risk_details_name`  varchar(255)                                                          DEFAULT NULL COMMENT '冗余风险小类名称',
    `category_id`        bigint                                                                DEFAULT NULL COMMENT '冗余风险大类ID，便于检索过滤和统计',
    `feature_code`       varchar(64)                                                           DEFAULT NULL COMMENT '特征业务编码，可选',
    `feature_text`       text                                                         NOT NULL COMMENT '攻击特征原文、payload、话术或安全例外样本',
    `normalized_text`    text COMMENT '归一化文本，用于 ES 检索和 hash 计算',
    `feature_type`       varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'KEYWORD' COMMENT '特征类型：KEYWORD、PAYLOAD、PROMPT_PATTERN、RESPONSE_PATTERN、JAILBREAK、SIMILAR_CASE、SAFE_EXCEPTION',
    `polarity`           varchar(32)                                                  NOT NULL DEFAULT 'UNSAFE' COMMENT '极性：UNSAFE-风险特征，SAFE_EXCEPTION-安全例外',
    `risk_level`         tinyint                                                      NOT NULL DEFAULT '2' COMMENT '风险等级：1-低，2-中，3-高，4-致命',
    `language`           varchar(16)                                                  NOT NULL DEFAULT 'zh-CN' COMMENT '语言：zh-CN、en-US、mixed 等',
    `tags`               json                                                                  DEFAULT NULL COMMENT '标签，例如 jailbreak、payload、fraud、weapon、self-harm',
    `source`             varchar(64)                                                           DEFAULT NULL COMMENT '来源：manual、dataset、redteam、incident、generated',
    `weight`             decimal(6, 3)                                                NOT NULL DEFAULT '1.000' COMMENT '特征权重，用于召回融合和阈值调整',
    `content_hash`       char(64)                                                     NOT NULL COMMENT 'feature_text 归一化后的 SHA-256，用于幂等去重',
    `version`            int                                                          NOT NULL DEFAULT '1' COMMENT '特征版本，每次内容变更递增',
    `sync_status`        tinyint                                                      NOT NULL DEFAULT '0' COMMENT '综合同步状态：0-待同步，1-已同步，2-同步失败，3-已删除待同步',
    `es_sync_status`     tinyint                                                      NOT NULL DEFAULT '0' COMMENT 'ES同步状态：0-待同步，1-已同步，2-同步失败',
    `milvus_sync_status` tinyint                                                      NOT NULL DEFAULT '0' COMMENT 'Milvus同步状态：0-待同步，1-已同步，2-同步失败',
    `pg_sync_status`     tinyint                                                      NOT NULL DEFAULT '0' COMMENT 'Milvus同步状态：0-待同步，1-已同步，2-同步失败',
    `status`             tinyint                                                      NOT NULL DEFAULT '1' COMMENT '业务状态：0-禁用，1-启用',
    `creator`            varchar(64)                                                           DEFAULT NULL COMMENT '创建人',
    `updater`            varchar(64)                                                           DEFAULT NULL COMMENT '更新人',
    `create_time`        datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_risk_attack_feature_hash` (`content_hash`,`deleted`),
    KEY                  `idx_risk_attack_feature_details` (`risk_details_id`),
    KEY                  `idx_risk_attack_feature_category` (`category_id`),
    KEY                  `idx_risk_attack_feature_sync` (`sync_status`,`es_sync_status`,`milvus_sync_status`),
    KEY                  `idx_risk_attack_feature_status` (`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大模型安全评测-攻击语义知识库表';



CREATE TABLE `kb_sync_event`
(
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `event_id`        VARCHAR(64) NOT NULL COMMENT '事件唯一ID，用于 MQ 幂等',
    `aggregate_type`  VARCHAR(32) NOT NULL COMMENT '聚合类型：ATTACK_FEATURE、DETAIL_RULE',
    `aggregate_id`    BIGINT      NOT NULL COMMENT '聚合ID，例如 risk_attack_feature.id 或 risk_detail_rule.id',
    `operation_type`  VARCHAR(16) NOT NULL COMMENT '操作类型：CREATE、UPDATE、DELETE、REINDEX',
    `risk_details_id` BIGINT               DEFAULT NULL COMMENT '风险小类ID',
    `content_hash`    CHAR(64)             DEFAULT NULL COMMENT '内容 hash',
    `version`         INT         NOT NULL DEFAULT 1 COMMENT '事件对应的数据版本',
    `payload`         JSON                 DEFAULT NULL COMMENT '事件载荷快照',
    `es_status`       TINYINT     NOT NULL DEFAULT 0 COMMENT 'ES处理状态：0-待处理，1-成功，2-失败',
    `milvus_status`   TINYINT     NOT NULL DEFAULT 0 COMMENT 'Milvus处理状态：0-待处理，1-成功，2-失败',
    `pg_status`       TINYINT     NOT NULL DEFAULT 0 COMMENT 'pg处理状态：0-待处理，1-成功，2-失败',
    `retry_count`     INT         NOT NULL DEFAULT 0 COMMENT '重试次数',
    `next_retry_time` DATETIME             DEFAULT NULL COMMENT '下次重试时间',
    `last_error`      TEXT                 DEFAULT NULL COMMENT '最近一次失败原因',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kb_sync_event_event_id` (`event_id`),
    KEY               `idx_kb_sync_event_aggregate` (`aggregate_type`, `aggregate_id`),
    KEY               `idx_kb_sync_event_status` (`es_status`, `milvus_status`, `next_retry_time`),
    KEY               `idx_kb_sync_event_details` (`risk_details_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库索引同步事件表';
