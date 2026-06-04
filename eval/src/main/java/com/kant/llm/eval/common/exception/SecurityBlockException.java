package com.kant.llm.eval.common.exception;

import lombok.Getter;

/**
 * L1 字面量拦截命中一级致命关键词时抛出的异常。
 *
 * <p>异常中携带命中的风险明细 ID 和字面量关键词，便于网关、审计和可观测性链路
 * 在不重新扫描 Prompt 的情况下记录精确拦截原因。</p>
 */
@Getter
public class SecurityBlockException extends ClientException {

    private final Long riskDetailsId;

    private final String keyword;

    public SecurityBlockException(Long riskDetailsId, String keyword) {
        super("L1 安全拦截命中，riskDetailsId=" + riskDetailsId + "，keyword=" + keyword);
        this.riskDetailsId = riskDetailsId;
        this.keyword = keyword;
    }
}
