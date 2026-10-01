-- D04发现闭环：保留原始发生，追加归并/复核/候选审核/独立验证/整改事实。
-- 必须在D01、D02、D03迁移后由人工审查并执行；本次开发未执行任何SQL。
-- CREATE IF NOT EXISTS不会升级旧表。若执行过早期D04两表切片，先人工应用配套upgrade脚本。
-- D03原本一运行只有一个会话；固定回归一运行可有多个用例，每个尝试独立会话。
-- 下列ALTER是一次性迁移，不能重复执行；由迁移记录判断是否已应用。
ALTER TABLE `agent_red_team_session`
    DROP INDEX `uk_agent_red_team_session_run`,
    ADD UNIQUE KEY `uk_agent_red_team_session_run_attempt` (`run_id`, `attempt_id`);

CREATE TABLE IF NOT EXISTS `agent_finding_state` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `finding_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '归并根原始发生标识',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '服务端业务空间',
    `actor_id` VARCHAR(64) NOT NULL COMMENT '当前访问归属操作者',
    `verification_status` VARCHAR(32) NOT NULL COMMENT 'SUSPECTED、CONFIRMED、DISMISSED、INCONCLUSIVE',
    `remediation_status` VARCHAR(32) NULL COMMENT '独立整改状态，首次确认只初始化OPEN',
    `severity` VARCHAR(32) NOT NULL COMMENT 'PENDING、LOW、MEDIUM、HIGH、CRITICAL',
    `owner_ref` VARCHAR(128) NULL COMMENT '整改负责人引用',
    `current_regression_run_id` VARCHAR(64) COLLATE utf8mb4_bin NULL COMMENT '当前指定固定回归',
    `repair_snapshot_ref` VARCHAR(160) COLLATE utf8mb4_bin NULL COMMENT '当前修复目标快照',
    `required_cases_json` LONGTEXT NULL COMMENT '当前全部正式用例固定版本',
    `resolved_by` VARCHAR(64) NULL COMMENT '确认修复认证负责人',
    `resolution_reason` VARCHAR(2000) NULL COMMENT '负责人确认修复依据',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '复核受理时递增的业务版本',
    `created_at` DATETIME(3) NOT NULL COMMENT '首次受理时间',
    `updated_at` DATETIME(3) NOT NULL COMMENT '最近受理时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_finding_state` (`finding_id`),
    KEY `idx_agent_finding_status` (`tenant_id`, `actor_id`, `verification_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发现确认与整改控制面';

CREATE TABLE IF NOT EXISTS `agent_finding_review` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `review_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '复核标识',
    `finding_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '被复核发现',
    `idempotency_key` VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '本发现内幂等键',
    `request_hash` CHAR(64) NOT NULL COMMENT '请求内容摘要，不公开原请求',
    `expected_version` BIGINT NOT NULL COMMENT '客户端原期望版本',
    `accepted_version` BIGINT NOT NULL COMMENT '首次受理后的版本及分页游标',
    `kind` VARCHAR(32) NOT NULL COMMENT 'EVIDENCE_CHECK或HUMAN',
    `opinion` VARCHAR(32) NOT NULL COMMENT 'SUPPORT、OPPOSE、INSUFFICIENT、REPLAY_REQUIRED',
    `decision` VARCHAR(32) NOT NULL COMMENT 'COMMENT_ONLY、CONFIRM、DISMISS、INCONCLUSIVE',
    `reason` VARCHAR(2000) NOT NULL COMMENT '简短依据，不包含目标原始响应',
    `evidence_refs_json` LONGTEXT NOT NULL COMMENT '本条意见的证据引用',
    `report_json` LONGTEXT NOT NULL COMMENT '检查器版本和当时完整证据索引快照，不含正文',
    `reviewer_ref` VARCHAR(64) NOT NULL COMMENT '服务端操作者',
    `severity` VARCHAR(32) NULL COMMENT '本次确认填写的严重度',
    `owner_ref` VARCHAR(128) NULL COMMENT '本次确认填写的负责人',
    `previous_verification_status` VARCHAR(32) NOT NULL COMMENT '受理前确认状态',
    `resulting_verification_status` VARCHAR(32) NOT NULL COMMENT '受理后确认状态',
    `created_at` DATETIME(3) NOT NULL COMMENT '首次受理时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_finding_review_id` (`review_id`),
    UNIQUE KEY `uk_agent_finding_review_key` (`finding_id`, `idempotency_key`),
    UNIQUE KEY `uk_agent_finding_review_version` (`finding_id`, `accepted_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追加式发现复核意见及证据快照';

-- D04完整后续表；所有ID/幂等键区分大小写，原始D02/D03事实不覆盖。

CREATE TABLE IF NOT EXISTS `agent_finding_occurrence` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `occurrence_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '原始发生标识',
    `finding_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '当前归并根',
    `tenant_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '业务空间',
    `actor_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '归属操作者',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_finding_occurrence` (`occurrence_id`),
    KEY `idx_finding_members` (`finding_id`, `tenant_id`, `actor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='原始发生归并映射';

CREATE TABLE IF NOT EXISTS `agent_finding_change` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `change_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '审计标识',
    `finding_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '作用的根发现',
    `accepted_version` BIGINT NOT NULL COMMENT '受理后版本及历史游标',
    `idempotency_key` VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '根发现内幂等键',
    `request_hash` CHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '请求摘要',
    `request_json` LONGTEXT NOT NULL COMMENT '命令与版本快照',
    `type` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT '人工变更或REGRESSION_HIT',
    `reason` VARCHAR(2000) COLLATE utf8mb4_bin NOT NULL COMMENT '依据',
    `actor_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '认证操作者',
    `result_json` LONGTEXT NOT NULL COMMENT '首次受理回执',
    `created_at` DATETIME(3) NOT NULL COMMENT '受理时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_finding_change_id` (`change_id`),
    UNIQUE KEY `uk_finding_change_key` (`finding_id`, `idempotency_key`),
    UNIQUE KEY `uk_finding_change_version` (`finding_id`, `accepted_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追加式发现归并和整改审计';

CREATE TABLE IF NOT EXISTS `agent_case_candidate` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `candidate_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '候选标识',
    `finding_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '根发现',
    `tenant_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '业务空间',
    `actor_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '归属操作者',
    `idempotency_key` VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '发现内创建幂等键',
    `request_hash` CHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '请求摘要',
    `definition_json` LONGTEXT NOT NULL COMMENT '冻结前置条件输入引用断言',
    `status` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'PENDING_REVIEW RETURNED APPROVED',
    `version` BIGINT NOT NULL COMMENT '候选控制版本',
    `existing_case_id` VARCHAR(96) COLLATE utf8mb4_bin NULL COMMENT '修订的用例',
    `base_case_version` BIGINT NULL COMMENT '修订依据版本',
    `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_case_candidate_id` (`candidate_id`),
    UNIQUE KEY `uk_case_candidate_key` (`finding_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待审核固定轨迹候选';

CREATE TABLE IF NOT EXISTS `agent_case_review` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `review_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '审核标识',
    `candidate_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '候选标识',
    `idempotency_key` VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '候选内幂等键',
    `request_hash` CHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '请求摘要',
    `decision` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'APPROVE RETURN',
    `reason` VARCHAR(2000) COLLATE utf8mb4_bin NOT NULL COMMENT '审核依据',
    `actor_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '认证审核人',
    `result_json` LONGTEXT NOT NULL COMMENT '首次审核回执',
    `created_at` DATETIME(3) NOT NULL COMMENT '审核时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_case_review_id` (`review_id`),
    UNIQUE KEY `uk_case_review_key` (`candidate_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追加式候选审核历史';

CREATE TABLE IF NOT EXISTS `agent_regression_case` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `case_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '正式用例标识',
    `case_version` BIGINT NOT NULL COMMENT '不可变版本',
    `candidate_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '来源候选',
    `finding_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '根发现',
    `tenant_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '业务空间',
    `actor_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '归属操作者',
    `definition_json` LONGTEXT NOT NULL COMMENT '冻结配置与原规则',
    `approval_ref` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '审核标识',
    `created_at` DATETIME(3) NOT NULL COMMENT '发布版本时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_regression_case_version` (`case_id`, `case_version`),
    UNIQUE KEY `uk_regression_case_candidate` (`candidate_id`),
    KEY `idx_regression_case_finding` (`finding_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变正式回归用例版本';

CREATE TABLE IF NOT EXISTS `agent_validation_run` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `run_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT 'vrun命名空间验证运行',
    `tenant_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '业务空间',
    `actor_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '归属操作者',
    `finding_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '根发现',
    `kind` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'REPLAY REGRESSION',
    `idempotency_key` VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '发现与用途内幂等键',
    `request_hash` CHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '完整请求摘要',
    `plan_json` LONGTEXT NOT NULL COMMENT '不可变固定执行计划',
    `status` VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT 'QUEUED RUNNING BLOCKED COMPLETED FAILED STOPPED',
    `version` BIGINT NOT NULL COMMENT '业务版本',
    `budget_json` LONGTEXT NOT NULL COMMENT '独立预算账本',
    `results_json` LONGTEXT NOT NULL COMMENT '新执行与新判定索引',
    `pending_command_json` LONGTEXT NULL COMMENT '受限在途命令含原文禁止公开',
    `pending_tokens` BIGINT NOT NULL COMMENT '在途输入Token预留',
    `pending_execution_id` VARCHAR(96) COLLATE utf8mb4_bin NULL COMMENT '未确认物理执行引用',
    `cursor` INT NOT NULL COMMENT '下一步0基索引',
    `worker_id` VARCHAR(64) COLLATE utf8mb4_bin NULL COMMENT '租约持有者',
    `lease_until` DATETIME(3) NULL COMMENT '租约期限',
    `stop_requested` TINYINT(1) NOT NULL COMMENT '停止意图',
    `error_code` VARCHAR(64) COLLATE utf8mb4_bin NULL COMMENT '稳定错误编码',
    `error_summary` VARCHAR(1000) COLLATE utf8mb4_bin NULL COMMENT '安全摘要',
    `created_at` DATETIME(3) NOT NULL COMMENT '受理时间',
    `deadline_at` DATETIME(3) NOT NULL COMMENT '原固定截止时间',
    `finished_at` DATETIME(3) NULL COMMENT '终态时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_validation_run_id` (`run_id`),
    UNIQUE KEY `uk_validation_run_key` (`finding_id`, `kind`, `idempotency_key`),
    KEY `idx_validation_recovery` (`status`, `lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='独立复现及标准评测固定回归运行';

CREATE TABLE IF NOT EXISTS `agent_validation_command` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `run_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '验证运行',
    `idempotency_key` VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '运行内停止幂等键',
    `request_hash` CHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '命令摘要',
    `result_json` LONGTEXT NOT NULL COMMENT '首次受理回执',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_validation_command_key` (`run_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追加式验证停止命令';

CREATE TABLE IF NOT EXISTS `agent_validation_assessment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `assessment_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '新判定标识',
    `run_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '验证运行',
    `assessment_json` LONGTEXT NOT NULL COMMENT '范围原规则判定器版本证据引用',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_validation_assessment` (`assessment_id`),
    KEY `idx_validation_assessment_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追加式新验证判定';

CREATE TABLE IF NOT EXISTS `agent_validation_occurrence` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `occurrence_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '新验证命中标识',
    `finding_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '根发现',
    `run_id` VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '验证运行',
    `case_index` INT NOT NULL COMMENT '用例0基序号，同一用例会话前缀只计一次发生',
    `assessment_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '新判定引用',
    `target_snapshot_ref` VARCHAR(160) COLLATE utf8mb4_bin NOT NULL COMMENT '本次目标快照',
    `created_at` DATETIME(3) NOT NULL COMMENT '发生时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_validation_occurrence` (`occurrence_id`),
    UNIQUE KEY `uk_validation_hit_assessment` (`assessment_id`),
    UNIQUE KEY `uk_validation_hit_case` (`run_id`, `case_index`),
    KEY `idx_validation_occurrence_finding` (`finding_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新验证命中发生事实';
