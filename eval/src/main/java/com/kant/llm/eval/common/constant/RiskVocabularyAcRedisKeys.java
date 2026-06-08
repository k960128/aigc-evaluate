package com.kant.llm.eval.common.constant;

/**
 * 风险词库 AC 自动机构建链路使用的 Redis Key 统一定义。
 *
 * <p>发布服务和集群节点监听器必须使用同一组 Key，避免快照写入、版本指针和通知通道
 * 之间出现命名不一致的问题。</p>
 */
public final class RiskVocabularyAcRedisKeys {

    /** 当前已发布的最新 AC 版本号。 */
    public static final String LATEST_VERSION = "risk:vocabulary:ac:latest_version";

    /** 当前最新版本对应的快照内容 hash，用于判断本次发布是否真的发生变化。 */
    public static final String LATEST_HASH = "risk:vocabulary:ac:latest_hash";

    /** Redis Pub/Sub 通道，节点通过它接收新版本构建通知。 */
    public static final String CHANNEL = "risk:vocabulary:ac:channel";

    /** 发布分布式锁，防止多个后台请求同时生成 AC 版本。 */
    public static final String PUBLISH_LOCK = "risk:vocabulary:ac:publish:lock";

    /** 单个版本快照 Key 前缀，完整 Key 为 prefix + versionId。 */
    private static final String SNAPSHOT_PREFIX = "risk:vocabulary:ac:snapshot:";

    private RiskVocabularyAcRedisKeys() {
    }

    /**
     * 根据版本号生成该版本对应的 Redis 快照 Key。
     */
    public static String snapshotKey(Long versionId) {
        return SNAPSHOT_PREFIX + versionId;
    }
}
