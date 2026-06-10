package com.kant.llm.eval.common.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

/**
 * L2 召回模式配置。
 *
 * <p>优先读取 app.l2.recall-mode；如果未配置，则兼容历史 app.l2.mock-recall-enabled。
 * 这样本地开发可以继续默认使用 MySQL Mock，真实 ES + PGVector 联调时只需要切到 real。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.l2")
public class L2RecallModeProperties {

    /** 召回模式：mysql-mock、empty、real。 */
    private String recallMode;

    /** 历史 Mock 开关：true-mysql-mock，false-empty。 */
    private Boolean mockRecallEnabled = Boolean.TRUE;

    /**
     * 解析当前 L2 召回模式。
     *
     * <p>新字段 recallMode 优先级更高；旧字段 mockRecallEnabled 只作为兼容兜底，避免历史环境升级后启动行为突变。</p>
     */
    public RecallMode resolveMode() {
        if (StringUtils.isNotBlank(recallMode)) {
            return RecallMode.from(recallMode);
        }
        return Boolean.FALSE.equals(mockRecallEnabled) ? RecallMode.EMPTY : RecallMode.MYSQL_MOCK;
    }

    public enum RecallMode {
        /** 从 MySQL Mock 知识库做轻量文本召回，适合无 ES/PG 的本地验证。 */
        MYSQL_MOCK,

        /** 空召回降级实现，适合临时关闭 L2 召回外部依赖。 */
        EMPTY,

        /** 真实双路召回：ES 负责关键词召回，PGVector 负责语义向量召回。 */
        REAL;

        /**
         * 将配置字符串解析为枚举。
         *
         * <p>保留 mock、es-pg 等别名，方便不同环境逐步迁移配置。</p>
         */
        public static RecallMode from(String value) {
            return switch (StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT)) {
                case "mysql-mock", "mock", "mysql_mock" -> MYSQL_MOCK;
                case "empty", "none", "degraded" -> EMPTY;
                case "real", "es-pg", "es_pg" -> REAL;
                default -> throw new IllegalArgumentException("Unsupported app.l2.recall-mode: " + value);
            };
        }
    }
}
