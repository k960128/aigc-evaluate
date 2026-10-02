package com.kant.agent.service.budget;

/**
 * 在模型服务没有返回 Token 用量时提供统一的保守估算。
 *
 * <p>该估算不是任何厂商分词器的精确结果，只用于当前 D02 的预算保护和
 * 可观测展示。真实适配器提供计量后，应优先使用真实值并保留计量来源。</p>
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    /**
     * 按 Unicode 码点数量的一半向上取整，至少返回一个 Token。
     *
     * <p>这种跨模型的估算会产生误差，因此调用方必须将结果标记为
     * {@code ESTIMATED}，不能把它描述成供应商返回的实际用量。</p>
     */
    public static long estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0L;
        }
        long codePointCount = text.codePoints().count();
        return Math.max(1L, (codePointCount + 1L) / 2L);
    }
}
