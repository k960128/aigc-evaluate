package com.kant.framework.mq.producer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.function.Consumer;

/**
 * 消息发送请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSendRequest {
    /**
     * 主题
     */
    String topic;
    /**
     * 业务 key，可用于幂等判断
     */
    String keys;
    /**
     * 业务描述，用于日志标识
     */
    String bizDesc;
    /**
     * 业务载荷
     */
    Object body;

    /**
     * 本地事务逻辑，在 half 消息发送成功后执行；抛异常则回滚消息
     */
    Consumer<Object> localTransaction;
}
