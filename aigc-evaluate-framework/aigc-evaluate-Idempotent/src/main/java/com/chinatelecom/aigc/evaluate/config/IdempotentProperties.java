package com.chinatelecom.aigc.evaluate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

/**
 * 幂等属性配置
 * @author AIGC
 */
@Data
@ConfigurationProperties(prefix = IdempotentProperties.PREFIX)
public class IdempotentProperties {

    public static final String PREFIX = "congomall.idempotent.token";

    /**
     * Token 幂等 Key 前缀
     */
    private String prefix;

    /**
     * Token 申请后过期时间
     * 单位默认毫秒 {@link TimeUnit#MILLISECONDS}
     */
    private Long timeout;
}
