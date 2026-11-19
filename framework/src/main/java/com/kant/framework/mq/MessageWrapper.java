package com.kant.framework.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * 消息体包装器
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageWrapper<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务 key
     */
    private String keys;

    /**
     * 业务载荷
     */
    private T body;

    /**
     * 业务描述，用于日志标识
     */
    String bizDesc;

    /**
     * 唯一标识，用于客户端幂等验证
     */
    @Builder.Default
    private String uuid = UUID.randomUUID().toString();

    /**
     * 消息发送时间
     */
    @Builder.Default
    private Long timestamp = System.currentTimeMillis();

    /**
     * 当前重试次数
     */
    @Builder.Default
    private int currentRetryCount = 0;
}
