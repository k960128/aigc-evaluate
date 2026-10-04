package com.kant.agent.service.budget;

/**
 * 在调用前及模型服务没有返回Token用量时提供字符估算，不保证总是高于真实用量。
 *
 * <p>该估算不是任何厂商分词器的精确结果，用于D02及D05等流程的预算预留和
 * 可观测展示。真实适配器提供计量后，应优先使用真实值并保留计量来源。</p>
 */
public final class TokenEstimator {

    /** 无状态工具，不创建实例或加载供应商分词器。 */
    private TokenEstimator() {
    }

    /**
     * 非空文本按Unicode码点数的一半向上取整，至少一个Token；null或空字符串返回0。
     *
     * <p>这种跨模型的估算会产生误差，因此调用方必须将结果标记为
     * {@code ESTIMATED}，不能把它描述成供应商返回的实际用量。</p>
     * @param text 待估算的提示词或响应文本，不记录正文日志
     * @return 非负Token估计值
     */
    public static long estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0L;
        }
        long codePointCount = text.codePoints().count();
        return Math.max(1L, (codePointCount + 1L) / 2L);
    }
}
