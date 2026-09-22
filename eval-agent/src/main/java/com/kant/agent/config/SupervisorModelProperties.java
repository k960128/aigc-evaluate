package com.kant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.Assert;

import java.time.Duration;

/**
 * 评测主智能体使用的规划模型配置。
 *
 * <p>这些配置由服务端维护，不能由模型输出或前端请求覆盖。</p>
 *
 * <p>当前实现通过 OpenAI 兼容协议调用规划模型。
 * 默认值对应本地 Ollama 的兼容接口。</p>
 *
 * @param baseUrl         模型服务根地址；默认请求路径为 /v1/chat/completions
 * @param apiKey          模型服务凭证；本地 Ollama 可以使用占位值 ollama
 * @param model           规划模型名称，与业务中的被测模型 ID 独立
 * @param connectTimeout  建立 HTTP 连接的超时时间
 * @param readTimeout     HTTP 请求读取响应的超时时间
 * @param maxOutputTokens 单次请求允许生成的最大输出 Token 数
 * @param maxPromptChars  完整提示词允许的最大字符数；不是 Token 预算
 */
@ConfigurationProperties(prefix = "eval-agent.supervisor")
public record SupervisorModelProperties(
        @DefaultValue("http://localhost:11434")
        String baseUrl,

        @DefaultValue("ollama")
        String apiKey,

        @DefaultValue("deepseek-r1:8b")
        String model,

        @DefaultValue("5s")
        Duration connectTimeout,

        @DefaultValue("120s")
        Duration readTimeout,

        @DefaultValue("2048")
        int maxOutputTokens,

        @DefaultValue("60000")
        int maxPromptChars
) {

    public SupervisorModelProperties {
        Assert.hasText(baseUrl, "规划模型 baseUrl 不能为空");
        Assert.hasText(apiKey, "规划模型 apiKey 不能为空");
        Assert.hasText(model, "规划模型 model 不能为空");

        Assert.notNull(connectTimeout, "connectTimeout 不能为空");
        Assert.notNull(readTimeout, "readTimeout 不能为空");

        Assert.isTrue(
                connectTimeout.compareTo(Duration.ZERO) > 0,
                "connectTimeout 必须大于 0"
        );
        Assert.isTrue(
                readTimeout.compareTo(Duration.ZERO) > 0,
                "readTimeout 必须大于 0"
        );
        Assert.isTrue(
                maxOutputTokens > 0,
                "maxOutputTokens 必须大于 0"
        );
        Assert.isTrue(
                maxPromptChars > 0,
                "maxPromptChars 必须大于 0"
        );

        baseUrl = baseUrl.strip();
        apiKey = apiKey.strip();
        model = model.strip();
    }

    /**
     * 避免 record 自动生成的 toString 将 API Key 打印到日志。
     */
    @Override
    public String toString() {
        return STR."SupervisorModelProperties[model=\{model}, apiKey=<redacted>]";
    }
}