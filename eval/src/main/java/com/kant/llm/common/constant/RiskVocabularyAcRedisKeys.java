package com.kant.llm.common.constant;

/**
 * AC 快照发布链路使用的 Redis 键。
 */
public final class RiskVocabularyAcRedisKeys {

    public static final String LATEST_VERSION = "risk:vocabulary:ac:latest_version";

    public static final String LATEST_HASH = "risk:vocabulary:ac:latest_hash";

    public static final String LATEST_MANIFEST = "risk:vocabulary:ac:latest_manifest";

    public static final String PUBLISH_LOCK = "risk:vocabulary:ac:publish:lock";

    private static final String SNAPSHOT_PREFIX = "risk:vocabulary:ac:snapshot:";

    private RiskVocabularyAcRedisKeys() {
    }

    public static String manifestKey(Long versionId) {
        return SNAPSHOT_PREFIX + versionId + ":manifest";
    }

    public static String shardKey(Long versionId, Long riskDetailsId, Integer bucketNo) {
        return SNAPSHOT_PREFIX + versionId + ":shard:" + riskDetailsId + ":" + bucketNo;
    }
}
