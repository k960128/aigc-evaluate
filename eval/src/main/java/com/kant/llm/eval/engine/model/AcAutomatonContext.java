package com.kant.llm.eval.engine.model;

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * JVM 内当前可用的一棵 AC 自动机快照。
 *
 * <p>对象创建后不再原地修改，L1 引擎只通过 AtomicReference 整体替换它。
 * 这样可以保证评测线程无锁读取，同时避免构建中的半成品 Trie 暴露给线上请求。</p>
 */
@Getter
@Builder
@AllArgsConstructor
public class AcAutomatonContext {

    /** AC 版本号；DB 兜底构建时可能为空。 */
    private final Long versionId;

    /** 快照内容 hash，用于排查节点加载版本是否一致。 */
    private final String hash;

    /** 进入当前 Trie 的去重后特征词数量。 */
    private final Integer wordCount;

    /** 当前 JVM 完成本次构建的时间。 */
    private final LocalDateTime buildTime;

    /** 真正执行字面量匹配的 Aho-Corasick Double Array Trie。 */
    private final AhoCorasickDoubleArrayTrie<RiskTag> trie;
}
