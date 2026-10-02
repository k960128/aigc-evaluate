-- D05独立样本工厂：种子/计划/批次/操作事实/候选/审核/正式资产，不修改旧dataset表。
-- 本文件仅新增迁移草案，本次开发没有执行SQL。人工审阅后在与eval相同的MySQL数据源应用。
-- CREATE IF NOT EXISTS不升级旧表；不得将其当成已有D05表的增量升级脚本。
-- 全部DATETIME(6)表示UTC无时区数据库时间；计划原IANA时区保存在schedule_json。
-- 标识、操作者、幂等键、摘要区分大小写；私有content/command/result需限制数据库访问。

CREATE TABLE IF NOT EXISTS `agent_sample_seed` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `seed_id` VARCHAR(96) NOT NULL COMMENT '不可变专家种子，精确版本1',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '服务端业务空间',
    `actor_id` VARCHAR(64) NOT NULL COMMENT '认证归属主体',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '主体内创建幂等键',
    `request_hash` CHAR(64) NOT NULL COMMENT '完整创建请求SHA256',
    `name` VARCHAR(256) NOT NULL COMMENT '种子展示名',
    `content` LONGTEXT NOT NULL COMMENT '受限种子原文，不做trim',
    `content_hash` CHAR(64) NOT NULL COMMENT '原文SHA256',
    `source_ref` VARCHAR(256) NULL COMMENT '专家案例来源，不自动访问外部URL',
    `created_at` DATETIME(6) NOT NULL COMMENT 'UTC创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sample_seed_id` (`seed_id`),
    UNIQUE KEY `uk_sample_seed_key` (`tenant_id`, `actor_id`, `idempotency_key`),
    KEY `idx_sample_seed_owner` (`tenant_id`, `actor_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='不可变专家种子';

CREATE TABLE IF NOT EXISTS `agent_sample_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `task_id` VARCHAR(96) NOT NULL COMMENT '样本生产任务',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '服务端业务空间',
    `actor_id` VARCHAR(64) NOT NULL COMMENT '认证归属主体',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '主体内创建幂等键',
    `request_hash` CHAR(64) NOT NULL COMMENT '完整创建配置摘要',
    `name` VARCHAR(256) NOT NULL COMMENT '计划名称',
    `activity_id` VARCHAR(64) NOT NULL COMMENT '评测活动引用',
    `authorization_ref` VARCHAR(128) NOT NULL COMMENT '外部生产批准引用，首版仅记录不验证授权系统',
    `spec_json` LONGTEXT NOT NULL COMMENT '冻结的种子、变异、提示词、风险及断言配置',
    `budget_json` LONGTEXT NOT NULL COMMENT '单批生成预算，与探测预算独立',
    `schedule_json` LONGTEXT NULL COMMENT '有界每日计划及IANA时区',
    `schedule_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '显式启用标识，创建默认false',
    `next_fire_at` DATETIME(6) NULL COMMENT 'UTC下一个原计划槽位',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '计划启停版本，技术游标不递增',
    `created_at` DATETIME(6) NOT NULL COMMENT 'UTC创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sample_task_id` (`task_id`),
    UNIQUE KEY `uk_sample_task_key` (`tenant_id`, `actor_id`, `idempotency_key`),
    KEY `idx_sample_task_due` (`schedule_enabled`, `next_fire_at`),
    KEY `idx_sample_task_owner` (`tenant_id`, `actor_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='冻结样本生产计划';

CREATE TABLE IF NOT EXISTS `agent_sample_run` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `run_id` VARCHAR(96) NOT NULL COMMENT '生产批次sprun命名空间',
    `task_id` VARCHAR(96) NOT NULL COMMENT '生产任务',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '服务端业务空间',
    `actor_id` VARCHAR(64) NOT NULL COMMENT '认证归属主体',
    `idempotency_key` VARCHAR(192) NOT NULL COMMENT '内部manual或scheduled命名空间受理键',
    `request_hash` CHAR(64) NOT NULL COMMENT '首次受理请求摘要',
    `trigger_key` VARCHAR(192) NOT NULL COMMENT 'manual键或slot原UTC时刻，计划内唯一',
    `trigger_kind` VARCHAR(32) NOT NULL COMMENT 'MANUAL、SCHEDULED、BACKFILL',
    `planned_for` DATETIME(6) NULL COMMENT '原UTC计划槽位，手动为空',
    `status` VARCHAR(32) NOT NULL COMMENT 'QUEUED、RUNNING、BLOCKED、STOPPED、COMPLETED、FAILED',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '批次控制与推进版本',
    `usage_json` LONGTEXT NOT NULL COMMENT '确认Token、预留Token与已受理调用次数',
    `ordinal` INT NOT NULL DEFAULT 0 COMMENT '下一个根槽位0基序号',
    `rewrite_round` INT NOT NULL DEFAULT 0 COMMENT '当前质量改写轮次',
    `parent_candidate_id` VARCHAR(96) NULL COMMENT '当前改写父候选',
    `pending_operation_id` VARCHAR(96) NULL COMMENT '尚未结算操作',
    `stop_requested` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '停止新增调用意图',
    `worker_id` VARCHAR(96) NULL COMMENT '租约主体',
    `lease_until` DATETIME(6) NULL COMMENT 'UTC租约截止或阻塞冷却',
    `deadline_at` DATETIME(6) NOT NULL COMMENT '从受理开始固定期限，不因恢复延期',
    `error_code` VARCHAR(96) NULL COMMENT '安全错误或停止编码',
    `created_at` DATETIME(6) NOT NULL COMMENT 'UTC受理时间',
    `finished_at` DATETIME(6) NULL COMMENT 'UTC终态时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sample_run_id` (`run_id`),
    UNIQUE KEY `uk_sample_run_slot` (`task_id`, `trigger_key`),
    UNIQUE KEY `uk_sample_run_key` (`task_id`, `idempotency_key`),
    KEY `idx_sample_run_recovery` (`status`, `lease_until`, `id`),
    KEY `idx_sample_run_task` (`task_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='有界样本生产批次与租约';

CREATE TABLE IF NOT EXISTS `agent_sample_run_receipt` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `task_id` VARCHAR(96) NOT NULL COMMENT '生产任务',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '人工运行请求键，不与调度键共用',
    `request_hash` CHAR(64) NOT NULL COMMENT '原槽位与版本的请求摘要',
    `run_id` VARCHAR(96) NOT NULL COMMENT '首次请求引用的实际批次',
    `result_json` LONGTEXT NOT NULL COMMENT '首次受理回执，即使多个键共享槽位也保存',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sample_run_receipt` (`task_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='手动及补跑幂等回执';

CREATE TABLE IF NOT EXISTS `agent_sample_operation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `operation_id` VARCHAR(96) NOT NULL COMMENT '确定性物理生成操作标识',
    `run_id` VARCHAR(96) NOT NULL COMMENT '生产批次',
    `ordinal` INT NOT NULL COMMENT '根槽位0基序号',
    `rewrite_round` INT NOT NULL COMMENT '当前改写轮次',
    `parent_candidate_id` VARCHAR(96) NULL COMMENT '原父候选',
    `command_json` LONGTEXT NOT NULL COMMENT '固定模型命令，包含受限种子/父正文',
    `reservation_tokens` BIGINT NOT NULL COMMENT '输入估算加输出配置上限的预留',
    `status` VARCHAR(32) NOT NULL COMMENT 'ACCEPTED、CALLING、SUCCEEDED、UNKNOWN、CANCELLED',
    `dispatch_worker_id` VARCHAR(96) NULL COMMENT '原物理调用者，迟到回执围栏',
    `result_json` LONGTEXT NULL COMMENT '已确认结构化结果和ACTUAL/ESTIMATED计量',
    `created_at` DATETIME(6) NOT NULL COMMENT 'UTC首次预留时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sample_operation_id` (`operation_id`),
    UNIQUE KEY `uk_sample_operation_round` (`run_id`, `ordinal`, `rewrite_round`),
    KEY `idx_sample_operation_run` (`run_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='固定生成操作与未知结果事实';

CREATE TABLE IF NOT EXISTS `agent_sample_candidate` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `candidate_id` VARCHAR(96) NOT NULL COMMENT '由生成操作确定的候选标识',
    `task_id` VARCHAR(96) NOT NULL COMMENT '生产任务',
    `run_id` VARCHAR(96) NOT NULL COMMENT '生产批次',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '服务端业务空间',
    `actor_id` VARCHAR(64) NOT NULL COMMENT '认证归属主体',
    `operation_id` VARCHAR(96) NOT NULL COMMENT '唯一来源操作',
    `ordinal` INT NOT NULL COMMENT '根槽位0基序号',
    `rewrite_round` INT NOT NULL COMMENT '质量改写轮次',
    `parent_candidate_id` VARCHAR(96) NULL COMMENT '上一轮候选，不覆盖失败样本',
    `seed_id` VARCHAR(96) NOT NULL COMMENT '原专家种子',
    `content` LONGTEXT NULL COMMENT '受限原文，拒绝/非法结构可为空',
    `content_hash` CHAR(64) NULL COMMENT '原文SHA256',
    `normalized_hash` CHAR(64) NULL COMMENT '质量PASS时的规范去重键',
    `duplicate_of_candidate_id` VARCHAR(96) NULL COMMENT '首个同规范键候选',
    `quality_json` LONGTEXT NOT NULL COMMENT '质检版本、结果及原因',
    `status` VARCHAR(32) NOT NULL COMMENT 'PENDING_REVIEW、QUALITY_REJECTED、DUPLICATE、APPROVED、RETURNED',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '人工审核版本',
    `created_at` DATETIME(6) NOT NULL COMMENT 'UTC候选形成时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sample_candidate_id` (`candidate_id`),
    UNIQUE KEY `uk_sample_candidate_operation` (`operation_id`),
    KEY `idx_sample_candidate_review` (`run_id`, `status`, `id`),
    KEY `idx_sample_candidate_lineage` (`run_id`, `ordinal`, `rewrite_round`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='生成候选、质检与人工准入状态';

CREATE TABLE IF NOT EXISTS `agent_sample_content_index` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '服务端业务空间',
    `actor_id` VARCHAR(64) NOT NULL COMMENT '认证归属主体',
    `dedup_version` VARCHAR(64) NOT NULL COMMENT '规范去重规则版本',
    `normalized_hash` CHAR(64) NOT NULL COMMENT 'NFKC空白与小写规则摘要，不是语义去重',
    `candidate_id` VARCHAR(96) NOT NULL COMMENT '首个质量PASS候选，退回也不删除索引',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sample_content_fingerprint` (`tenant_id`, `actor_id`, `dedup_version`, `normalized_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='跨批次规范精确去重索引';

CREATE TABLE IF NOT EXISTS `agent_sample_review` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `review_id` VARCHAR(96) NOT NULL COMMENT '人工审核记录',
    `candidate_id` VARCHAR(96) NOT NULL COMMENT '审核候选',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '候选内审核幂等键',
    `request_hash` CHAR(64) NOT NULL COMMENT '人工请求摘要',
    `reason` VARCHAR(2000) NOT NULL COMMENT '人工审核依据',
    `decision` VARCHAR(32) NOT NULL COMMENT 'APPROVE或RETURN',
    `result_json` LONGTEXT NOT NULL COMMENT '含认证审核者和正式资产引用的首次回执',
    `created_at` DATETIME(6) NOT NULL COMMENT 'UTC受理时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sample_review_id` (`review_id`),
    UNIQUE KEY `uk_sample_review_key` (`candidate_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='人工审核与启用依据';

CREATE TABLE IF NOT EXISTS `agent_sample_asset` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `case_id` VARCHAR(96) NOT NULL COMMENT '正式单消息用例',
    `case_version` BIGINT NOT NULL COMMENT '不可变精确版本，首版1',
    `candidate_id` VARCHAR(96) NOT NULL COMMENT '已批准候选',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '服务端业务空间',
    `actor_id` VARCHAR(64) NOT NULL COMMENT '认证归属主体',
    `approval_ref` VARCHAR(96) NOT NULL COMMENT '人工审核记录',
    `definition_json` LONGTEXT NOT NULL COMMENT '输入引用/摘要/前置/断言/原规则不可变配置',
    `created_at` DATETIME(6) NOT NULL COMMENT 'UTC启用时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sample_asset_version` (`case_id`, `case_version`),
    UNIQUE KEY `uk_sample_asset_candidate` (`candidate_id`),
    KEY `idx_sample_asset_owner` (`tenant_id`, `actor_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='审核后启用的正式样本资产';

CREATE TABLE IF NOT EXISTS `agent_sample_case_set` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `case_set_id` VARCHAR(96) NOT NULL COMMENT '固定正式用例集',
    `case_set_version` BIGINT NOT NULL COMMENT '不可变精确版本，首版1',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '服务端业务空间',
    `actor_id` VARCHAR(64) NOT NULL COMMENT '认证归属主体',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '主体内集合创建幂等键',
    `request_hash` CHAR(64) NOT NULL COMMENT '名称与有序引用集合摘要',
    `name` VARCHAR(256) NOT NULL COMMENT '集合名称',
    `cases_json` LONGTEXT NOT NULL COMMENT '已启用caseId和精确version，不接受候选ID',
    `created_at` DATETIME(6) NOT NULL COMMENT 'UTC创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sample_case_set_version` (`case_set_id`, `case_set_version`),
    UNIQUE KEY `uk_sample_case_set_key` (`tenant_id`, `actor_id`, `idempotency_key`),
    KEY `idx_sample_case_set_owner` (`tenant_id`, `actor_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='可选择的固定正式样本集合';

CREATE TABLE IF NOT EXISTS `agent_sample_command` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `resource_id` VARCHAR(96) NOT NULL COMMENT '任务或批次控制资源',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '资源内命令键',
    `request_hash` CHAR(64) NOT NULL COMMENT '完整控制请求摘要',
    `result_json` LONGTEXT NOT NULL COMMENT '首次启停/停止回执',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sample_control_key` (`resource_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='版本化控制命令幂等回执';

CREATE TABLE IF NOT EXISTS `agent_sample_schedule_fire` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `task_id` VARCHAR(96) NOT NULL COMMENT '生产任务',
    `planned_for` DATETIME(6) NOT NULL COMMENT '原计划槽位或遗漏范围起点',
    `kind` VARCHAR(32) NOT NULL COMMENT 'QUEUED、MISSED、MISSED_RANGE',
    `run_id` VARCHAR(96) NULL COMMENT '已创建/已存在批次，遗漏为空',
    `details_json` LONGTEXT NOT NULL COMMENT '时区、政策或遗漏范围汇总',
    `created_at` DATETIME(6) NOT NULL COMMENT 'UTC实际受理时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sample_schedule_slot` (`task_id`, `planned_for`),
    KEY `idx_sample_schedule_history` (`task_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='定时触发与遗漏审计';
