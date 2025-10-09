ALTER TABLE `task_answer`
    ADD COLUMN `violation` bigint(10) NULL DEFAULT 0 COMMENT '自动判定结果' AFTER `judge_result`,
ADD COLUMN `think_process` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '推理过程' AFTER `violation`;