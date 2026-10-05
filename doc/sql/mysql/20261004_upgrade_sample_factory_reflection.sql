-- D05 Reflection增量迁移草案：只调整执行检查点和操作唯一键，不删除种子、计划、候选或历史结果。
-- 本次未执行。已有库先备份，停止所有旧版Worker/调度器并等待在途调用退出，再人工执行并部署新版。
-- 新库也须先执行20261001_create_agent_sample_factory_d05.sql，再执行本文件一次；本文件不支持重复执行。
-- 不可滚动混跑新旧Worker：旧代码无法识别REFLECT操作。DDL可能等待元数据锁，应安排维护窗口。

ALTER TABLE `agent_sample_run`
    ADD COLUMN `workflow_version` VARCHAR(64) NOT NULL DEFAULT 'sample-generation-v1'
        COMMENT '历史批次保持旧版本，新代码显式写sample-reflection-v1' AFTER `status`,
    ADD COLUMN `phase` VARCHAR(32) NOT NULL DEFAULT 'GENERATE'
        COMMENT 'GENERATE生成或改写，REFLECT模型评审，终态保留检查点' AFTER `workflow_version`;

-- 为同一槽位同一改写轮次允许且仅允许一次生成和一次评审。
-- 历史操作默认GENERATE；替换的是唯一索引，不删除任何物理调用事实。
ALTER TABLE `agent_sample_operation`
    ADD COLUMN `operation_kind` VARCHAR(32) NOT NULL DEFAULT 'GENERATE'
        COMMENT 'GENERATE生成或改写、REFLECT模型评审' AFTER `rewrite_round`,
    ADD UNIQUE KEY `uk_sample_operation_phase` (`run_id`, `ordinal`, `rewrite_round`, `operation_kind`),
    DROP INDEX `uk_sample_operation_round`;

-- agent_sample_candidate.status为VARCHAR，无需改类型；新增REFLECTING表示尚未完成模型评审，不可审核入库。
-- 配置指纹加入Reflection/评审版本，旧计划需显式重置或新建，不能批量UPDATE旧profileRef。
-- 旧非终态批次只结算已有SUCCEEDED；未发送请求取消，CALLING/UNKNOWN仍保留，绝不为升级自动重发。
