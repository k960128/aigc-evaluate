-- D02：反馈驱动的自动红队任务事实。
--
-- 本脚本只保存任务配置、运行状态、尝试、动作和疑似发现，不复制输入与响应正文。
-- 输入、响应和错误正文继续由 D01 的 agent_evidence 表保存，D02 只保存证据引用。
-- 当前 D02 是单风险、单策略、单尝试的自适应单轮闭环；后续多风险、多轮和异步
-- 恢复会在现有事实表上扩展，不通过修改历史记录伪造新的会话语义。

CREATE TABLE IF NOT EXISTS `agent_red_team_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `task_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '自动红队任务标识',
    `idempotency_key` VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '创建任务幂等键，区分大小写',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户或业务空间标识',
    `actor_id` VARCHAR(64) NOT NULL COMMENT '创建任务的操作者标识',
    `activity_id` VARCHAR(64) NOT NULL COMMENT '评测活动标识',
    `authorization_ref` VARCHAR(128) NOT NULL COMMENT '运行授权引用',
    `target_type` VARCHAR(32) NOT NULL COMMENT '目标类型，D02 为 MODEL',
    `target_id` VARCHAR(64) NOT NULL COMMENT '目标资源标识',
    `target_snapshot_ref` VARCHAR(160) NOT NULL COMMENT '创建任务时固定的目标快照引用',
    `risk_id` VARCHAR(128) NOT NULL COMMENT '本次任务的风险编码',
    `rule_set_version` VARCHAR(128) NOT NULL COMMENT '判定规则集版本',
    `execution_mode` VARCHAR(40) NOT NULL COMMENT '任务执行模式，D02 为 ADAPTIVE_SINGLE_TURN',
    `strategy_version` VARCHAR(128) NOT NULL COMMENT '任务固定的策略版本',
    `seed_input` LONGTEXT NOT NULL COMMENT '首轮攻击种子正文；任务配置受限数据',
    `risk_indicators_json` LONGTEXT NOT NULL COMMENT '代码侧疑似风险信号 JSON 数组',
    `budget_policy_json` LONGTEXT NOT NULL COMMENT '不可变预算策略 JSON',
    `status` VARCHAR(32) NOT NULL COMMENT 'READY、RUNNING、COMPLETED、FAILED、STOPPED 或 BLOCKED',
    `stop_reason` VARCHAR(64) NULL COMMENT '最近一次运行停止原因',
    `stop_message` VARCHAR(1000) NULL COMMENT '最近一次运行停止摘要',
    `created_at` DATETIME(3) NOT NULL COMMENT '任务创建时间',
    `started_at` DATETIME(3) NULL COMMENT '最近一次运行开始时间',
    `finished_at` DATETIME(3) NULL COMMENT '最近一次运行结束时间',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '任务状态并发控制版本',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_red_team_task_id` (`task_id`),
    UNIQUE KEY `uk_agent_red_team_task_tenant_idempotency` (`tenant_id`, `idempotency_key`),
    KEY `idx_agent_red_team_task_tenant_status` (`tenant_id`, `actor_id`, `status`),
    KEY `idx_agent_red_team_task_target` (`tenant_id`, `target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='自动红队任务配置快照';

CREATE TABLE IF NOT EXISTS `agent_red_team_run` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `run_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '自动红队运行标识',
    `task_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属任务标识',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户或业务空间标识',
    `actor_id` VARCHAR(64) NOT NULL COMMENT '运行发起操作者标识',
    `status` VARCHAR(32) NOT NULL COMMENT 'QUEUED、RUNNING、BLOCKED、COMPLETED、FAILED 或 STOPPED',
    `target_calls` BIGINT NOT NULL DEFAULT 0 COMMENT '已预留或已发生的目标调用数',
    `agent_calls` BIGINT NOT NULL DEFAULT 0 COMMENT '已预留或已发生的 Agent 调用数',
    `attempts` BIGINT NOT NULL DEFAULT 0 COMMENT '已预留或已创建的攻击尝试数',
    `tokens` BIGINT NOT NULL DEFAULT 0 COMMENT '已结算或保守记账的Token数，当前D02为估算值',
    `reserved_tokens` BIGINT NOT NULL DEFAULT 0 COMMENT '尚未完成结算的Token预留数',
    `action_count` BIGINT NOT NULL DEFAULT 0 COMMENT '已记录的攻击动作数，包含结束和被拒绝动作',
    `current_attempt_id` VARCHAR(96) COLLATE utf8mb4_bin NULL COMMENT '当前攻击尝试标识',
    `stop_reason` VARCHAR(64) NULL COMMENT '运行停止原因',
    `stop_message` VARCHAR(1000) NULL COMMENT '运行停止摘要',
    `stop_requested` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收到用户停止意图',
    `created_at` DATETIME(3) NOT NULL COMMENT '运行创建时间',
    `started_at` DATETIME(3) NULL COMMENT '运行开始时间',
    `finished_at` DATETIME(3) NULL COMMENT '运行结束时间',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '运行状态并发控制版本',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_red_team_run_id` (`run_id`),
    KEY `idx_agent_red_team_run_tenant_task_status` (`tenant_id`, `task_id`, `status`),
    KEY `idx_agent_red_team_run_actor_status` (`tenant_id`, `actor_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='自动红队一次运行的状态和预算摘要';

CREATE TABLE IF NOT EXISTS `agent_red_team_attempt` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `attempt_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '攻击尝试标识',
    `task_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属任务标识',
    `run_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属运行标识',
    `sequence` INT NOT NULL COMMENT '运行内尝试序号',
    `risk_id` VARCHAR(128) NOT NULL COMMENT '尝试对应的风险编码',
    `strategy_version` VARCHAR(128) NOT NULL COMMENT '尝试使用的策略版本',
    `status` VARCHAR(32) NOT NULL COMMENT '尝试状态，复用运行状态语义',
    `probe_count` INT NOT NULL DEFAULT 0 COMMENT '已获得观察的目标探测数',
    `stop_reason` VARCHAR(64) NULL COMMENT '尝试停止原因',
    `stop_message` VARCHAR(1000) NULL COMMENT '尝试停止摘要',
    `started_at` DATETIME(3) NOT NULL COMMENT '尝试开始时间',
    `finished_at` DATETIME(3) NULL COMMENT '尝试结束时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_red_team_attempt_id` (`attempt_id`),
    KEY `idx_agent_red_team_attempt_run_sequence` (`run_id`, `sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='自动红队攻击尝试事实';

CREATE TABLE IF NOT EXISTS `agent_red_team_action` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `action_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '攻击动作标识',
    `task_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属任务标识',
    `run_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属运行标识',
    `attempt_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属攻击尝试标识',
    `sequence` INT NOT NULL COMMENT '尝试内动作序号',
    `action_type` VARCHAR(32) NOT NULL COMMENT 'SEND_INPUT 或 END_ATTEMPT',
    `strategy_version` VARCHAR(128) NOT NULL COMMENT '动作使用的策略版本',
    `input_hash` CHAR(64) NULL COMMENT '输入正文 SHA-256 摘要',
    `reason_summary` VARCHAR(2000) NOT NULL COMMENT '面向审计的动作依据摘要',
    `observation_refs_json` LONGTEXT NOT NULL COMMENT '动作引用的历史观察 JSON 数组',
    `suggested_stop_reason` VARCHAR(64) NULL COMMENT 'Agent 建议的停止原因',
    `actual_stop_reason` VARCHAR(64) NULL COMMENT '编排器确认的停止原因',
    `input_evidence_ref` VARCHAR(96) COLLATE utf8mb4_bin NULL COMMENT 'D01 输入证据引用',
    `probe_id` VARCHAR(64) COLLATE utf8mb4_bin NULL COMMENT '逻辑探测标识',
    `execution_id` VARCHAR(96) COLLATE utf8mb4_bin NULL COMMENT 'D01 物理执行标识',
    `execution_status` VARCHAR(32) NULL COMMENT 'D01 目标执行状态',
    `response_evidence_ref` VARCHAR(96) COLLATE utf8mb4_bin NULL COMMENT 'D01 响应证据引用',
    `error_code` VARCHAR(64) NULL COMMENT 'D01 错误编码或编排错误编码',
    `risk_observed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否命中任务配置的疑似风险信号',
    `action_status` VARCHAR(32) NOT NULL COMMENT 'PROPOSED、EXECUTED、EXECUTION_UNKNOWN、REJECTED、STOPPED 或 ENDED',
    `created_at` DATETIME(3) NOT NULL COMMENT '动作首次创建时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_red_team_action_id` (`action_id`),
    KEY `idx_agent_red_team_action_run_sequence` (`run_id`, `sequence`),
    KEY `idx_agent_red_team_action_execution` (`execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='自动红队动作意图和目标执行引用';

CREATE TABLE IF NOT EXISTS `agent_red_team_finding` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `finding_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '疑似发现标识',
    `task_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属任务标识',
    `run_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属运行标识',
    `attempt_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属攻击尝试标识',
    `action_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '触发发现的动作标识',
    `risk_id` VARCHAR(128) NOT NULL COMMENT '风险编码',
    `evidence_refs_json` LONGTEXT NOT NULL COMMENT '输入、响应和错误证据引用 JSON 数组',
    `reason` VARCHAR(2000) NOT NULL COMMENT '规则信号命中的说明',
    `verification_status` VARCHAR(32) NOT NULL COMMENT 'D02 固定为 SUSPECTED',
    `created_at` DATETIME(3) NOT NULL COMMENT '疑似发现创建时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_red_team_finding_id` (`finding_id`),
    KEY `idx_agent_red_team_finding_run_status` (`run_id`, `verification_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='自动红队疑似风险发现';
