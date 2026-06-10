-- L2 real ES + PGVector reindex seed script.
-- Execute after 20260609_seed_l2_mock_knowledge.sql when you want existing active mock features
-- to be synchronized into the real ES index and PGVector table.
--
-- This script is idempotent:
-- 1. Active risk_attack_feature rows are reset to pending sync.
-- 2. One deterministic ATTACK_FEATURE/REINDEX event is created per active feature.
-- 3. Existing deterministic events are reset to pending for another synchronization run.

SET NAMES utf8mb4;

UPDATE `risk_attack_feature`
SET
  `sync_status` = 0,
  `es_sync_status` = 0,
  `milvus_sync_status` = 0,
  `updater` = 'codex_reindex',
  `update_time` = CURRENT_TIMESTAMP
WHERE `status` = 1
  AND `deleted` = 0;

INSERT INTO `kb_sync_event` (
  `event_id`,
  `aggregate_type`,
  `aggregate_id`,
  `operation_type`,
  `risk_details_id`,
  `content_hash`,
  `version`,
  `payload`,
  `es_status`,
  `milvus_status`,
  `retry_count`,
  `next_retry_time`,
  `last_error`
)
SELECT
  CONCAT('R', LEFT(SHA2(CONCAT_WS('|',
    'ATTACK_FEATURE',
    'REINDEX',
    CAST(`id` AS CHAR),
    CAST(`version` AS CHAR),
    `content_hash`
  ), 256), 63)) AS `event_id`,
  'ATTACK_FEATURE' AS `aggregate_type`,
  `id` AS `aggregate_id`,
  'REINDEX' AS `operation_type`,
  `risk_details_id`,
  `content_hash`,
  `version`,
  JSON_OBJECT(
    'id', `id`,
    'riskDetailsId', `risk_details_id`,
    'categoryId', `category_id`,
    'featureCode', `feature_code`,
    'featureText', `feature_text`,
    'normalizedText', `normalized_text`,
    'featureType', `feature_type`,
    'polarity', `polarity`,
    'riskLevel', `risk_level`,
    'language', `language`,
    'tags', `tags`,
    'source', `source`,
    'weight', `weight`,
    'contentHash', `content_hash`,
    'version', `version`,
    'status', `status`
  ) AS `payload`,
  0 AS `es_status`,
  0 AS `milvus_status`,
  0 AS `retry_count`,
  NULL AS `next_retry_time`,
  NULL AS `last_error`
FROM `risk_attack_feature`
WHERE `status` = 1
  AND `deleted` = 0
ON DUPLICATE KEY UPDATE
  `es_status` = VALUES(`es_status`),
  `milvus_status` = VALUES(`milvus_status`),
  `retry_count` = VALUES(`retry_count`),
  `next_retry_time` = VALUES(`next_retry_time`),
  `last_error` = VALUES(`last_error`),
  `update_time` = CURRENT_TIMESTAMP;
