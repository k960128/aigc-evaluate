package com.kant.llm.eval.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * L2 知识库索引同步配置。
 *
 * <p>该配置只控制后台同步 worker，不影响 L2 评测主流程是否启动。
 * 默认关闭是为了让没有 ES/PGVector 的开发环境仍能正常使用 MySQL Mock 召回。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.l2.index-sync")
public class L2KnowledgeIndexSyncProperties {

    /** 是否启用本地定时同步 worker，默认关闭，避免无 ES/PG 时影响启动。 */
    private boolean enabled = false;

    /** 定时扫描间隔，单位毫秒。 */
    private long fixedDelayMs = 30000L;

    /** 每批处理的 kb_sync_event 数量；批次越大同步越快，但单次事务时间也会更长。 */
    private int batchSize = 50;
}
