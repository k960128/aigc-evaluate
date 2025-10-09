-- MySQL dump 10.13  Distrib 8.0.25, for Linux (x86_64)
--
-- Host: localhost    Database: aigc_evaluate
-- ------------------------------------------------------
-- Server version       8.0.25

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `aigc_evaluate`
--

CREATE DATABASE IF NOT EXISTS aigc_evaluate DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `aigc_evaluate`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for QRTZ_BLOB_TRIGGERS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_BLOB_TRIGGERS`;
CREATE TABLE `QRTZ_BLOB_TRIGGERS`  (
                                       `SCHED_NAME` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                       `TRIGGER_NAME` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                       `TRIGGER_GROUP` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                       `BLOB_DATA` blob NULL,
                                       PRIMARY KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) USING BTREE,
                                       INDEX `SCHED_NAME`(`SCHED_NAME` ASC, `TRIGGER_NAME` ASC, `TRIGGER_GROUP` ASC) USING BTREE,
                                       CONSTRAINT `qrtz_blob_triggers_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `QRTZ_TRIGGERS` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------
-- Records of QRTZ_BLOB_TRIGGERS
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for QRTZ_CALENDARS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_CALENDARS`;
CREATE TABLE `QRTZ_CALENDARS`  (
                                   `SCHED_NAME` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                   `CALENDAR_NAME` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                   `CALENDAR` blob NOT NULL,
                                   PRIMARY KEY (`SCHED_NAME`, `CALENDAR_NAME`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------
-- Records of QRTZ_CALENDARS
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for QRTZ_CRON_TRIGGERS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_CRON_TRIGGERS`;
CREATE TABLE `QRTZ_CRON_TRIGGERS`  (
                                       `SCHED_NAME` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                       `TRIGGER_NAME` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                       `TRIGGER_GROUP` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                       `CRON_EXPRESSION` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                       `TIME_ZONE_ID` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                                       PRIMARY KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) USING BTREE,
                                       CONSTRAINT `qrtz_cron_triggers_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `QRTZ_TRIGGERS` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;


-- ----------------------------
-- Table structure for QRTZ_FIRED_TRIGGERS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_FIRED_TRIGGERS`;
CREATE TABLE `QRTZ_FIRED_TRIGGERS`  (
                                        `SCHED_NAME` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                        `ENTRY_ID` varchar(95) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                        `TRIGGER_NAME` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                        `TRIGGER_GROUP` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                        `INSTANCE_NAME` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                        `FIRED_TIME` bigint NOT NULL,
                                        `SCHED_TIME` bigint NOT NULL,
                                        `PRIORITY` int NOT NULL,
                                        `STATE` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                        `JOB_NAME` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                                        `JOB_GROUP` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                                        `IS_NONCONCURRENT` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                                        `REQUESTS_RECOVERY` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                                        PRIMARY KEY (`SCHED_NAME`, `ENTRY_ID`) USING BTREE,
                                        INDEX `IDX_QRTZ_FT_TRIG_INST_NAME`(`SCHED_NAME` ASC, `INSTANCE_NAME` ASC) USING BTREE,
                                        INDEX `IDX_QRTZ_FT_INST_JOB_REQ_RCVRY`(`SCHED_NAME` ASC, `INSTANCE_NAME` ASC, `REQUESTS_RECOVERY` ASC) USING BTREE,
                                        INDEX `IDX_QRTZ_FT_J_G`(`SCHED_NAME` ASC, `JOB_NAME` ASC, `JOB_GROUP` ASC) USING BTREE,
                                        INDEX `IDX_QRTZ_FT_JG`(`SCHED_NAME` ASC, `JOB_GROUP` ASC) USING BTREE,
                                        INDEX `IDX_QRTZ_FT_T_G`(`SCHED_NAME` ASC, `TRIGGER_NAME` ASC, `TRIGGER_GROUP` ASC) USING BTREE,
                                        INDEX `IDX_QRTZ_FT_TG`(`SCHED_NAME` ASC, `TRIGGER_GROUP` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;


-- ----------------------------
-- Table structure for QRTZ_JOB_DETAILS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_JOB_DETAILS`;
CREATE TABLE `QRTZ_JOB_DETAILS`  (
                                     `SCHED_NAME` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `JOB_NAME` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `JOB_GROUP` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `DESCRIPTION` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                                     `JOB_CLASS_NAME` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `IS_DURABLE` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `IS_NONCONCURRENT` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `IS_UPDATE_DATA` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `REQUESTS_RECOVERY` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                     `JOB_DATA` blob NULL,
                                     PRIMARY KEY (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`) USING BTREE,
                                     INDEX `IDX_QRTZ_J_REQ_RECOVERY`(`SCHED_NAME` ASC, `REQUESTS_RECOVERY` ASC) USING BTREE,
                                     INDEX `IDX_QRTZ_J_GRP`(`SCHED_NAME` ASC, `JOB_GROUP` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for QRTZ_LOCKS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_LOCKS`;
CREATE TABLE `QRTZ_LOCKS`  (
                               `SCHED_NAME` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                               `LOCK_NAME` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                               PRIMARY KEY (`SCHED_NAME`, `LOCK_NAME`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------
-- Records of QRTZ_LOCKS
-- ----------------------------
BEGIN;
INSERT INTO `QRTZ_LOCKS` (`SCHED_NAME`, `LOCK_NAME`) VALUES ('schedulerName', 'STATE_ACCESS'), ('schedulerName', 'TRIGGER_ACCESS');
COMMIT;

-- ----------------------------
-- Table structure for QRTZ_PAUSED_TRIGGER_GRPS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_PAUSED_TRIGGER_GRPS`;
CREATE TABLE `QRTZ_PAUSED_TRIGGER_GRPS`  (
                                             `SCHED_NAME` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                             `TRIGGER_GROUP` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                             PRIMARY KEY (`SCHED_NAME`, `TRIGGER_GROUP`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for QRTZ_SCHEDULER_STATE
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_SCHEDULER_STATE`;
CREATE TABLE `QRTZ_SCHEDULER_STATE`  (
                                         `SCHED_NAME` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                         `INSTANCE_NAME` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                         `LAST_CHECKIN_TIME` bigint NOT NULL,
                                         `CHECKIN_INTERVAL` bigint NOT NULL,
                                         PRIMARY KEY (`SCHED_NAME`, `INSTANCE_NAME`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;


-- ----------------------------
-- Table structure for QRTZ_SIMPLE_TRIGGERS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_SIMPLE_TRIGGERS`;
CREATE TABLE `QRTZ_SIMPLE_TRIGGERS`  (
                                         `SCHED_NAME` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                         `TRIGGER_NAME` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                         `TRIGGER_GROUP` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                         `REPEAT_COUNT` bigint NOT NULL,
                                         `REPEAT_INTERVAL` bigint NOT NULL,
                                         `TIMES_TRIGGERED` bigint NOT NULL,
                                         PRIMARY KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) USING BTREE,
                                         CONSTRAINT `qrtz_simple_triggers_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `QRTZ_TRIGGERS` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;


-- ----------------------------
-- Table structure for QRTZ_SIMPROP_TRIGGERS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_SIMPROP_TRIGGERS`;
CREATE TABLE `QRTZ_SIMPROP_TRIGGERS`  (
                                          `SCHED_NAME` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                          `TRIGGER_NAME` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                          `TRIGGER_GROUP` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                          `STR_PROP_1` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                                          `STR_PROP_2` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                                          `STR_PROP_3` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                                          `INT_PROP_1` int NULL DEFAULT NULL,
                                          `INT_PROP_2` int NULL DEFAULT NULL,
                                          `LONG_PROP_1` bigint NULL DEFAULT NULL,
                                          `LONG_PROP_2` bigint NULL DEFAULT NULL,
                                          `DEC_PROP_1` decimal(13, 4) NULL DEFAULT NULL,
                                          `DEC_PROP_2` decimal(13, 4) NULL DEFAULT NULL,
                                          `BOOL_PROP_1` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                                          `BOOL_PROP_2` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                                          PRIMARY KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) USING BTREE,
                                          CONSTRAINT `qrtz_simprop_triggers_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `QRTZ_TRIGGERS` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;


-- ----------------------------
-- Table structure for QRTZ_TRIGGERS
-- ----------------------------
DROP TABLE IF EXISTS `QRTZ_TRIGGERS`;
CREATE TABLE `QRTZ_TRIGGERS`  (
                                  `SCHED_NAME` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                  `TRIGGER_NAME` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                  `TRIGGER_GROUP` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                  `JOB_NAME` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                  `JOB_GROUP` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                  `DESCRIPTION` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                                  `NEXT_FIRE_TIME` bigint NULL DEFAULT NULL,
                                  `PREV_FIRE_TIME` bigint NULL DEFAULT NULL,
                                  `PRIORITY` int NULL DEFAULT NULL,
                                  `TRIGGER_STATE` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                  `TRIGGER_TYPE` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                  `START_TIME` bigint NOT NULL,
                                  `END_TIME` bigint NULL DEFAULT NULL,
                                  `CALENDAR_NAME` varchar(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
                                  `MISFIRE_INSTR` smallint NULL DEFAULT NULL,
                                  `JOB_DATA` blob NULL,
                                  PRIMARY KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) USING BTREE,
                                  INDEX `IDX_QRTZ_T_J`(`SCHED_NAME` ASC, `JOB_NAME` ASC, `JOB_GROUP` ASC) USING BTREE,
                                  INDEX `IDX_QRTZ_T_JG`(`SCHED_NAME` ASC, `JOB_GROUP` ASC) USING BTREE,
                                  INDEX `IDX_QRTZ_T_C`(`SCHED_NAME` ASC, `CALENDAR_NAME` ASC) USING BTREE,
                                  INDEX `IDX_QRTZ_T_G`(`SCHED_NAME` ASC, `TRIGGER_GROUP` ASC) USING BTREE,
                                  INDEX `IDX_QRTZ_T_STATE`(`SCHED_NAME` ASC, `TRIGGER_STATE` ASC) USING BTREE,
                                  INDEX `IDX_QRTZ_T_N_STATE`(`SCHED_NAME` ASC, `TRIGGER_NAME` ASC, `TRIGGER_GROUP` ASC, `TRIGGER_STATE` ASC) USING BTREE,
                                  INDEX `IDX_QRTZ_T_N_G_STATE`(`SCHED_NAME` ASC, `TRIGGER_GROUP` ASC, `TRIGGER_STATE` ASC) USING BTREE,
                                  INDEX `IDX_QRTZ_T_NEXT_FIRE_TIME`(`SCHED_NAME` ASC, `NEXT_FIRE_TIME` ASC) USING BTREE,
                                  INDEX `IDX_QRTZ_T_NFT_ST`(`SCHED_NAME` ASC, `TRIGGER_STATE` ASC, `NEXT_FIRE_TIME` ASC) USING BTREE,
                                  INDEX `IDX_QRTZ_T_NFT_MISFIRE`(`SCHED_NAME` ASC, `MISFIRE_INSTR` ASC, `NEXT_FIRE_TIME` ASC) USING BTREE,
                                  INDEX `IDX_QRTZ_T_NFT_ST_MISFIRE`(`SCHED_NAME` ASC, `MISFIRE_INSTR` ASC, `NEXT_FIRE_TIME` ASC, `TRIGGER_STATE` ASC) USING BTREE,
                                  INDEX `IDX_QRTZ_T_NFT_ST_MISFIRE_GRP`(`SCHED_NAME` ASC, `MISFIRE_INSTR` ASC, `NEXT_FIRE_TIME` ASC, `TRIGGER_GROUP` ASC, `TRIGGER_STATE` ASC) USING BTREE,
                                  CONSTRAINT `qrtz_triggers_ibfk_1` FOREIGN KEY (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`) REFERENCES `QRTZ_JOB_DETAILS` (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;


-- ----------------------------
-- Table structure for job_info
-- ----------------------------
DROP TABLE IF EXISTS `job_info`;
CREATE TABLE `job_info`  (
                             `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务编号',
                             `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
                             `status` tinyint(4) NOT NULL COMMENT '任务状态',
                             `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '任务描述',
                             `handler_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '处理器的名字',
                             `handler_param` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '处理器的参数',
                             `max_thread_size` bigint(20) NULL DEFAULT 1 COMMENT '此任务最大线程数',
                             `run_type` tinyint(4) NULL DEFAULT NULL COMMENT '任务类型',
                             `cron_expression` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'CRON 表达式',
                             `one_time_expression` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '一次性任务表达式',
                             `retry_count` int(11) NULL DEFAULT 0 COMMENT '重试次数',
                             `retry_interval` int(11) NULL DEFAULT 0 COMMENT '重试间隔',
                             `monitor_timeout` int(11) NULL DEFAULT 0 COMMENT '监控超时时间',
                             `start_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
                             `end_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '结束时间',
                             `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
                             `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
                             `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
                             PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '定时任务表' ROW_FORMAT = Dynamic;




-- ----------------------------
-- Table structure for job_log
-- ----------------------------
DROP TABLE IF EXISTS `job_log`;
CREATE TABLE `job_log` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志编号',
                           `job_id` bigint NOT NULL COMMENT '任务编号',
                           `handler_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '处理器的名字',
                           `handler_param` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器的参数',
                           `execute_index` tinyint NOT NULL DEFAULT '1' COMMENT '第几次执行',
                           `begin_time` datetime NOT NULL COMMENT '开始执行时间',
                           `end_time` datetime DEFAULT NULL COMMENT '结束执行时间',
                           `duration` int DEFAULT NULL COMMENT '执行时长',
                           `status` tinyint NOT NULL COMMENT '任务状态',
                           `result` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '结果数据',
                           `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
                           `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
                           `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
                           PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务日志表';


#============================2025.03.10补充建表脚本
DROP TABLE IF EXISTS `question_info`;
CREATE TABLE `question_info` (
                                 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                 `question_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目唯一编号',
                                 `title` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目内容',
                                 `category` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属题库',
                                 `tags` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签',
                                 `content_hash` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目内容MD5哈希值',
                                 `difficulty` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目难度',
                                 `attack_method` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '攻击方式',
                                 `version` int DEFAULT '1' COMMENT '题目版本号',
                                 `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
                                 `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
                                 `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
                                 PRIMARY KEY (`id`) USING BTREE,
                                 FULLTEXT INDEX `title_idx`(`title`),
                                 INDEX `category_idx`(`category`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库题目信息表';




#============================2025.03.11补充建表脚本
DROP TABLE IF EXISTS `question_snapshot`;
CREATE TABLE `question_snapshot` (
                                       `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID自增',
                                       `question_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目唯一编号',
                                       `title` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目内容',
                                       `category` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属题库',
                                       `tags` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签',
                                       `content_hash` char(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目内容MD5哈希值',
                                       `difficulty` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目难度',
                                       `attack_method` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '攻击方式',
                                       `version` int(11) DEFAULT '1' COMMENT '题目版本号',
                                       `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
                                       `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
                                       `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                       `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
                                       PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库题目变更快照表';


DROP TABLE IF EXISTS `question_info_log`;
CREATE TABLE `question_info_log` (
                                     `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                     `question_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目唯一编号',
                                     `operation_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型|CRUD',
                                     `operation_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作方式|手动|批量',
                                     `source_version` int(11) DEFAULT '1' COMMENT '来源目版本号',
                                     `target_version` int(11) DEFAULT '1' COMMENT '变更题目版本号',
                                     `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
                                     `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
                                     `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
                                     PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库题目操作日志表';




#============================2025.03.12补充建表脚本 题集信息表
DROP TABLE IF EXISTS `question_set_info`;
CREATE TABLE `question_set_info` (
                                     `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                     `question_set_name` varchar(100) CHARACTER SET utf8mb4 NOT NULL COMMENT '题集名称',
                                     `question_category` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所选题库',
                                     `evaluation_target` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测评目标',
                                     `extract_conf` json NOT NULL COMMENT '抽取题目维度配置',
                                     `description` text CHARACTER SET utf8mb4 COMMENT '描述',
                                     `generate` bit(1) DEFAULT b'0' COMMENT '是否生成题集',
                                     `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
                                     `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
                                     `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
                                     PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题集信息表';

#=====================================习题集生成映射表
DROP TABLE IF EXISTS `question_set_item`;
CREATE TABLE `question_set_item` (
                                     `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID主键',
                                     `question_set_id` bigint(20)  NOT NULL COMMENT '归属习题集ID',
                                     `question_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目唯一ID',
                                     `question_version` int(11) DEFAULT '1' COMMENT '题目版本号',
                                     `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
                                     `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
                                     `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
                                     PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='习题集生成映射表';



DROP TABLE IF EXISTS `question_tag_info`;
CREATE TABLE `question_tag_info`  (
                                      `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                      `tag_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签ID',
                                      `tag_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
                                      `tag_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标签描述',
                                      `tag_level` int(3) NULL DEFAULT 1 COMMENT '标签级别',
                                      `parent_id` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '父级标签ID',
                                      `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标签类型',
                                      `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
                                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
                                      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
                                      PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '标签信息表' ROW_FORMAT = Dynamic;


-- ----------------------------
-- Records of question_tag_info
-- ----------------------------
BEGIN;
INSERT INTO `question_tag_info` VALUES (1, '1901904175617454080', '包含违反社会主义核心价值观的内容', '包含违反社会主义核心价值观的内容', 1, '0', '', '1', '2025-03-18 15:51:06', '1', '2025-03-18 15:51:06', b'0');
INSERT INTO `question_tag_info` VALUES (2, '1901904497547063296', '煽动颠覆国家政权、推翻社会主义制度', '煽动颠覆国家政权、推翻社会主义制度', 2, '1901904175617454080', '', '1', '2025-03-18 15:52:23', '1', '2025-03-18 15:52:23', b'0');
INSERT INTO `question_tag_info` VALUES (3, '1901904682515869696', '危害国家安全和利益、损害国家形象', '危害国家安全和利益、损害国家形象', 2, '1901904175617454080', '', '1', '2025-03-18 15:53:07', '1', '2025-03-18 15:53:07', b'0');
INSERT INTO `question_tag_info` VALUES (4, '1901904739046699008', '煽动分裂国家、破坏国家统一和社会稳定', '煽动分裂国家、破坏国家统一和社会稳定', 2, '1901904175617454080', '', '1', '2025-03-18 15:53:20', '1', '2025-03-18 15:53:20', b'0');
INSERT INTO `question_tag_info` VALUES (5, '1901904776858349568', '宣扬恐怖主义、极端主义', '宣扬恐怖主义、极端主义', 2, '1901904175617454080', '', '1', '2025-03-18 15:53:29', '1', '2025-03-18 15:53:29', b'0');
INSERT INTO `question_tag_info` VALUES (6, '1901904877110603776', '宣扬民族仇恨', '宣扬民族仇恨', 2, '1901904175617454080', '', '1', '2025-03-18 15:53:53', '1', '2025-03-18 15:53:53', b'0');
INSERT INTO `question_tag_info` VALUES (7, '1901904921188544512', '宣扬暴力、淫秽色情', '宣扬暴力、淫秽色情', 2, '1901904175617454080', '', '1', '2025-03-18 15:54:04', '1', '2025-03-18 15:54:04', b'0');
INSERT INTO `question_tag_info` VALUES (8, '1901904956047405056', '传播虚假有害信息', '传播虚假有害信息', 2, '1901904175617454080', '', '1', '2025-03-18 15:54:12', '1', '2025-03-18 15:54:12', b'0');
INSERT INTO `question_tag_info` VALUES (9, '1901904980932210688', '其他法律、行政法规禁止的内容', '其他法律、行政法规禁止的内容', 2, '1901904175617454080', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (10, '1901904980932210689', '包含歧视性内容', '包含歧视性内容', 1, '0', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (11, '1901904980932210694', '民族歧视内容', '民族歧视内容', 2, '1901904980932210689', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (12, '1901904980932210695', '信仰歧视内容', '信仰歧视内容', 2, '1901904980932210689', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (13, '1901904980932210696', '国别歧视内容', '国别歧视内容', 2, '1901904980932210689', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (14, '1901904980932210697', '地域歧视内容', '地域歧视内容', 2, '1901904980932210689', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (15, '1901904980932210698', '性别歧视内容', '性别歧视内容', 2, '1901904980932210689', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (16, '1901904980932210699', '年龄歧视内容', '年龄歧视内容', 2, '1901904980932210689', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (17, '1901904980932210700', '职业歧视内容', '职业歧视内容', 2, '1901904980932210689', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (18, '1901904980932210701', '健康歧视内容', '健康歧视内容', 2, '1901904980932210689', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (19, '1901904980932210702', '其他方面歧视内容', '其他方面歧视内容', 2, '1901904980932210689', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (20, '1901904980932210690', '商业违法违规', '商业违法违规', 1, '0', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (21, '1901904980932210703', '侵犯他人知识产权', '侵犯他人知识产权', 2, '1901904980932210690', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (22, '1901904980932210704', '违反商业道德', '违反商业道德', 2, '1901904980932210690', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (23, '1901904980932210705', '泄露他人商业秘密', '泄露他人商业秘密', 2, '1901904980932210690', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (24, '1901904980932210706', '利用算法、数据、平台等优势，实施垄断和不正当竞争行为', '利用算法、数据、平台等优势，实施垄断和不正当竞争行为', 2, '1901904980932210690', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (25, '1901904980932210707', '其他商业违法违规行为', '其他商业违法违规行为', 2, '1901904980932210690', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (26, '1901904980932210691', '侵犯他人合法权益', '侵犯他人合法权益', 1, '0', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (27, '1901904980932210708', '危害他人身心健康', '危害他人身心健康', 2, '1901904980932210691', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (28, '1901904980932210709', '侵害他人肖像权', '侵害他人肖像权', 2, '1901904980932210691', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (29, '1901904980932210710', '侵害他人名誉权', '侵害他人名誉权', 2, '1901904980932210691', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (30, '1901904980932210712', '侵害他人荣誉权', '侵害他人荣誉权', 2, '1901904980932210691', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (31, '1901904980932210713', '侵害他人隐私权', '侵害他人隐私权', 2, '1901904980932210691', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (32, '1901904980932210714', '侵害他人个人信息权益', '侵害他人个人信息权益', 2, '1901904980932210691', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (33, '1901904980932210715', '侵犯他人其他合法权益', '侵犯他人其他合法权益', 2, '1901904980932210691', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (34, '1901904980932210692', '无法满足特定服务类型的安全需求', '无法满足特定服务类型的安全需求', 1, '0', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (35, '1901904980932210716', '内容不准确，严重不符合科学常识或主流认知', '内容不准确，严重不符合科学常识或主流认知', 2, '1901904980932210692', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (36, '1901904980932210717', '内容不可靠，虽然不包含严重错误的内容，但无法对使用者形成帮助', '内容不可靠，虽然不包含严重错误的内容，但无法对使用者形成帮助', 2, '1901904980932210692', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (37, '1901904980932210693', '其他', '其他', 1, '0', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 15:54:18', b'0');
INSERT INTO `question_tag_info` VALUES (38, '1901904980932210718', '其他', '其他', 2, '1901904980932210693', '', '1', '2025-03-18 15:54:18', '1', '2025-03-18 08:13:16', b'0');
INSERT INTO `question_tag_info` VALUES (39, '1901904980932210719', '其他1', '其他1', 2, '1901904980932210693', '', '1', '2025-03-18 15:54:18', '1', '2025-03-19 01:40:59', b'1');
COMMIT;

-- ----------------------------
-- Table structure for model_info
-- ----------------------------
DROP TABLE IF EXISTS `model_info`;
CREATE TABLE `model_info`  (
                               `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '模型ID',
                               `app_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用名称',
                               `model_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
                               `model_version` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型版本号',
                               `max_thread_size` bigint(20) NULL DEFAULT 5 COMMENT '最大并发数',
                               `model_describe` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型描述',
                               `model_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型url',
                               `model_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型路径',
                               `model_req` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型请求对象',
                               `model_handler` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型处理函数',
                               `origin_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '环境参数',
                               `max_completion_tokens` bigint(10) NULL DEFAULT 500 COMMENT '生成词元数量',
                               `stream` bit(1) NULL DEFAULT b'0' COMMENT '流式响应',
                               `appid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '应用id',
                               `apikeys` varbinary(255) NULL DEFAULT NULL COMMENT '应用密钥',
                               `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '模型表' ROW_FORMAT = Dynamic;


-- ----------------------------
-- Table structure for model_item
-- ----------------------------
DROP TABLE IF EXISTS `model_item`;
CREATE TABLE `model_item`  (
                               `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '模型ID',
                               `model_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
                               `model_version` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型版本号',
                               `max_thread_size` bigint(20) NULL DEFAULT 5 COMMENT '最大并发数',
                               `model_describe` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型描述',
                               `model_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型url',
                               `model_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型路径',
                               `model_req` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型请求对象',
                               `max_completion_tokens` bigint(10) NULL DEFAULT 500 COMMENT '生成词元数量',
                               `stream` bit(1) NULL DEFAULT b'0' COMMENT '流式响应',
                               `model_handler` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型处理函数',
                               `origin_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '环境参数',
                               `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '模型表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of model_item
-- ----------------------------
BEGIN;
INSERT INTO `model_item` VALUES (1, '星河', 'v1.0', 8, '星河大模型', 'https://10.37.69.190:10443', '/aipaas/lm/v1/x/telechatCompletions', 'telechat-115b', 1500, b'1', 'xinghe', 'eop-auth-v1', '2025-03-20 01:28:17', '2025-03-20 01:30:09');
INSERT INTO `model_item` VALUES (2, 'deepseek-1p5', 'v1.0', 8, 'DeepSeek-1.5B', 'https://10.37.69.190:10443', '/aipaas/lm/v1/ds/ds1p5', 'deepseek-r1:1.5b', 1500, b'1', 'deepseek', 'eop-auth-v1', '2025-03-20 01:28:17', '2025-03-20 01:30:09');
INSERT INTO `model_item` VALUES (3, 'deepseek-7b', 'v1.0', 8, 'DeepSeek-7B', 'https://10.37.69.190:10443', '/aipaas/lm/v1/ds/ds7', 'deepseek-r1:7b', 1500, b'1', 'deepseek', 'eop-auth-v1', '2025-03-20 01:28:17', '2025-03-20 01:30:09');
INSERT INTO `model_item` VALUES (4, 'deepseek-8b', 'v1.0', 8, 'DeepSeek-8B', 'https://10.37.69.190:10443', '/aipaas/lm/v1/ds/ds8', 'deepseek-r1:8b', 1500, b'1', 'deepseek', 'eop-auth-v1', '2025-03-20 01:28:17', '2025-03-20 01:30:09');
INSERT INTO `model_item` VALUES (5, 'deepseek-14b', 'v1.0', 8, 'DeepSeek-14B', 'https://10.37.69.190:10443', '/aipaas/lm/v1/ds/ds14', 'deepseek-r1:14b', 1500, b'1', 'deepseek', 'eop-auth-v1', '2025-03-20 01:28:17', '2025-03-20 01:30:09');
INSERT INTO `model_item` VALUES (6, 'deepseek-32b', 'v1.0', 8, 'DeepSeek-32B', 'https://10.37.69.190:10443', '/aipaas/lm/v1/ds/ds32', 'deepseek-r1:32b', 1500, b'1', 'deepseek', 'eop-auth-v1', '2025-03-20 01:28:17', '2025-03-20 01:30:09');
INSERT INTO `model_item` VALUES (7, 'deepseek-r1_671b', 'v1.0', 8, 'DeepSeek-R1_671B', 'https://10.37.69.190:10443', '/aipaas/lm/v1/ds/ds671', 'DeepSeek-R1-Full', 1500, b'1', 'deepseek', 'eop-auth-v1', '2025-03-20 01:28:17', '2025-03-20 01:30:09');
COMMIT;

-- ----------------------------
-- Table structure for task_answer
-- ----------------------------
DROP TABLE IF EXISTS `task_answer`;
CREATE TABLE `task_answer`  (
                                `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                `question_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '题目唯一编号',
                                `question_version` bigint(20) NOT NULL COMMENT '题目版本号',
                                `question_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '题目内容',
                                `answer_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '题目答案',
                                `question_category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '所属题库',
                                `judge_result` bigint(10) NULL DEFAULT 0 COMMENT '人工审核结果',
                                `violation` bigint(10) NULL DEFAULT 0 COMMENT '自动判定结果',
                                `think_process` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '推理过程',
                                `model_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模型名字',
                                `model_version` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模型版本',
                                `app_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用名称',
                                `question_set` bigint(20) NULL DEFAULT NULL COMMENT '习题集',
                                `task_id` bigint(20) NOT NULL COMMENT '任务ID',
                                `model_id` bigint(20) NOT NULL,
                                `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                `creator` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '',
                                `updater` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '',
                                `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
                                PRIMARY KEY (`id`) USING BTREE,
                                INDEX `question_category_idx`(`question_category`) USING BTREE,
                                FULLTEXT INDEX `question_content_idx`(`question_content`),
                                FULLTEXT INDEX `answer_content_idx`(`answer_content`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '任务答案表' ROW_FORMAT = Dynamic;

#--------------------2025.03.21 题目标签关联表
DROP TABLE IF EXISTS `question_tag_mapping`;
CREATE TABLE `question_tag_mapping` (
                                        `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                        `question_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目唯一编号',
                                        `tag_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签ID',
                                        `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
                                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                        `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
                                        `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                        `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
                                        PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目-标签关联表';


CREATE TABLE `import_question_log` (
                                       `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
                                       `batch_no` bigint NOT NULL COMMENT '导入批次号',
                                       `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态：SUCCESS|FAILED',
                                       `content` json DEFAULT NULL COMMENT '内容',
                                       `file_name` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件信息',
                                       `run_state` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '运行状态',
                                       `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
                                       `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
                                       `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                       `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
                                       PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入题目日志表';