package com.kant.llm.eval.simple.dto;


import java.time.Instant;

/** 默认只返回配置/证据索引，受限种子与候选正文走独立认证接口。 */
public final class SimpleSeedFactoryResponses {

    /**
     * 种子元数据，原文通过独立认证接口获取。
     * @param seedId 专家种子业务标识
     * @param version 不可变种子版本，当前固定1
     * @param name 展示名称
     * @param contentHash 原文SHA-256，不是去重规范化后的摘要
     * @param sourceRef 可空来源引用，不自动访问该引用
     * @param createdAt 种子创建UTC时间
     */
    public record Seed(String seedId, long version, String name, String contentHash, String sourceRef,
                       Instant createdAt) {
    }

    /**
     * 独立认证接口返回的受限正文，前端必须按不可信纯文本展示。
     * @param resourceId 本次查询的种子、候选或正式资产ID
     * @param content 原始正文；非法生成或拒绝生成的候选可为空
     * @param contentHash 可空原文SHA-256，不经过去重规范化
     */
    public record Content(String resourceId, String content, String contentHash) {
    }
}
