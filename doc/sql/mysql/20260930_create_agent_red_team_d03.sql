-- D03：线性多轮目标会话与可恢复检查点。
-- 会话正文不复制到本表；每一轮的输入、响应和错误仍由 D01 的 agent_evidence 保存。
-- agent_probe_execution.session_ref 关联本表 session_id，恢复时通过真实执行事实重建历史。

CREATE TABLE IF NOT EXISTS `agent_red_team_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `session_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '平台会话标识，同时作为D01 sessionRef',
    `task_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属任务标识',
    `run_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属运行标识',
    `attempt_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属攻击尝试标识',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户或业务空间标识',
    `actor_id` VARCHAR(64) NOT NULL COMMENT '发起操作者标识',
    `target_snapshot_ref` VARCHAR(160) NOT NULL COMMENT '会话绑定的目标快照',
    `status` VARCHAR(32) NOT NULL COMMENT '会话当前状态',
    `turn_count` INT NOT NULL DEFAULT 0 COMMENT '已完成的目标消息轮数',
    `next_sequence` INT NOT NULL DEFAULT 1 COMMENT '下一条攻击动作序号',
    `last_probe_id` VARCHAR(64) COLLATE utf8mb4_bin NULL COMMENT '最近一次已确认探测',
    `last_execution_id` VARCHAR(96) COLLATE utf8mb4_bin NULL COMMENT '最近一次已确认物理执行',
    `pause_requested` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收到暂停意图',
    `stop_requested` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收到停止意图',
    `checkpoint_version` BIGINT NOT NULL DEFAULT 0 COMMENT '最新检查点版本',
    `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
    `closed_at` DATETIME(3) NULL COMMENT '关闭时间',
    `error_code` VARCHAR(64) NULL COMMENT '稳定错误编码',
    `error_message` VARCHAR(1000) NULL COMMENT '可安全展示的错误摘要',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_red_team_session_id` (`session_id`),
    UNIQUE KEY `uk_agent_red_team_session_run` (`run_id`),
    KEY `idx_agent_red_team_session_status` (`tenant_id`, `actor_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='自动红队线性多轮目标会话控制面';

-- Worker 租约字段位于运行表，保证进程重启后可以从 QUEUED、RUNNING 或
-- PAUSING记录重新接管；暂停/阻塞收到停止意图时也由Worker收敛。
-- 本ALTER为一次性迁移，必须在D01、D02之后执行且不得重复执行。
-- MySQL不支持这里的ADD COLUMN/KEY IF NOT EXISTS；由迁移工具记录是否已应用。
ALTER TABLE `agent_red_team_run`
    ADD COLUMN `worker_id` VARCHAR(96) COLLATE utf8mb4_bin NULL COMMENT '当前运行Worker租约标识',
    ADD COLUMN `worker_lease_until` DATETIME(3) NULL COMMENT 'Worker租约失效时间',
    ADD KEY `idx_agent_red_team_run_worker_lease` (`status`, `worker_lease_until`);

CREATE TABLE IF NOT EXISTS `agent_red_team_session_checkpoint` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `checkpoint_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '检查点标识',
    `session_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属会话',
    `task_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属任务',
    `run_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属运行',
    `attempt_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属尝试',
    `checkpoint_version` BIGINT NOT NULL COMMENT '会话内单调递增版本',
    `turn_count` INT NOT NULL COMMENT '已完成轮数',
    `next_sequence` INT NOT NULL COMMENT '下一条动作序号',
    `last_probe_id` VARCHAR(64) COLLATE utf8mb4_bin NULL COMMENT '最近一次探测',
    `last_execution_id` VARCHAR(96) COLLATE utf8mb4_bin NULL COMMENT '最近一次物理执行',
    `history_hash` CHAR(64) NOT NULL COMMENT '已确认历史摘要',
    `status` VARCHAR(32) NOT NULL COMMENT '保存检查点时的会话状态',
    `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_red_team_checkpoint_id` (`checkpoint_id`),
    UNIQUE KEY `uk_agent_red_team_checkpoint_version` (`session_id`, `checkpoint_version`),
    KEY `idx_agent_red_team_checkpoint_session` (`session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='自动红队多轮会话追加式恢复检查点';

-- command_json/action_json含受限输入，不能通过通用CRUD或日志公开。
CREATE TABLE IF NOT EXISTS `agent_red_team_operation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `run_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属运行',
    `sequence` INT NOT NULL COMMENT '固定动作序号',
    `action_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '固定动作标识',
    `action_json` LONGTEXT NOT NULL COMMENT '校验后的攻击动作，受限正文',
    `command_json` LONGTEXT NOT NULL COMMENT '固定探测命令及幂等键，受限正文',
    `reserved_tokens` BIGINT NOT NULL COMMENT '本操作的输入上下文预留',
    `status` VARCHAR(32) NOT NULL COMMENT 'PREPARED、DISPATCHING、SETTLED',
    `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_red_team_operation` (`run_id`, `sequence`),
    KEY `idx_agent_red_team_operation_status` (`run_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受限多轮操作意图日志';

CREATE TABLE IF NOT EXISTS `agent_red_team_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'SSE补发游标和事件标识',
    `run_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属运行',
    `event_type` VARCHAR(64) NOT NULL COMMENT '带契约版本的事件类型',
    `status` VARCHAR(32) NOT NULL COMMENT '事件发生时运行状态',
    `run_version` BIGINT NOT NULL COMMENT '本运行单调递增资源版本',
    `summary` VARCHAR(2000) NOT NULL COMMENT '安全摘要，不含证据正文',
    `created_at` DATETIME(3) NOT NULL COMMENT '事件发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_agent_red_team_event_cursor` (`run_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追加式运行事件';

CREATE TABLE IF NOT EXISTS `agent_red_team_command` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `command_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '全局命令标识',
    `run_id` VARCHAR(96) COLLATE utf8mb4_bin NOT NULL COMMENT '所属运行',
    `idempotency_key` VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '本运行内命令幂等键',
    `actor_id` VARCHAR(64) NOT NULL COMMENT '服务端操作者',
    `type` VARCHAR(32) NOT NULL COMMENT 'PAUSE、RESUME、STOP',
    `expected_version` BIGINT NOT NULL COMMENT '客户端期望版本',
    `accepted_version` BIGINT NOT NULL COMMENT '首次受理后的版本',
    `created_at` DATETIME(3) NOT NULL COMMENT '首次受理时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_red_team_command` (`run_id`, `idempotency_key`),
    UNIQUE KEY `uk_agent_red_team_command_id` (`command_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等运行控制命令';
