package com.kant.llm.eval.agent.probe.support;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 将第三方客户端异常转换为可保存、可展示的受控错误摘要。
 *
 * <p>异常文本来自外部 SDK，可能包含认证头、API Key 或超长响应。本组件只做
 * 最低限度的脱敏与截断，普通日志仍只记录错误类型和业务标识。</p>
 */
@Component
public class SensitiveErrorSanitizer {

    private static final int MAX_SAFE_MESSAGE_LENGTH = 1000;
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*)([^,;\\s]+)");
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "(?i)((?:api[-_ ]?key|access[-_ ]?token)\\s*[:=]\\s*)([^,;\\s]+)");
    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)(bearer\\s+)[a-z0-9._\\-+/=]+");
    private static final Pattern OPENAI_STYLE_KEY_PATTERN = Pattern.compile(
            "(?i)sk-[a-z0-9_\\-]{8,}");

    /**
     * 生成不含已知凭据形式的错误摘要。
     *
     * @param throwable 原始异常
     * @return 可持久化的简短摘要
     */
    public String sanitize(Throwable throwable) {
        if (throwable == null) {
            return "未获得可确认的目标调用结果";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        String sanitized = AUTHORIZATION_PATTERN.matcher(message).replaceAll("$1[REDACTED]");
        sanitized = API_KEY_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
        sanitized = OPENAI_STYLE_KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED]");
        sanitized = sanitized.replace('\r', ' ').replace('\n', ' ').trim();
        if (sanitized.length() > MAX_SAFE_MESSAGE_LENGTH) {
            return sanitized.substring(0, MAX_SAFE_MESSAGE_LENGTH);
        }
        return sanitized;
    }
}
