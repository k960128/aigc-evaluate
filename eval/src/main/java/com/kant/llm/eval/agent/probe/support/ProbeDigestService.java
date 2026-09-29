package com.kant.llm.eval.agent.probe.support;

import com.kant.agent.contract.common.ExecutionContext;
import com.kant.agent.contract.probe.ProbeCommand;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 计算探测请求指纹和证据内容摘要。
 *
 * <p>请求指纹不包含每次 HTTP 重试都会变化的 {@code traceId}，但包含身份、
 * 授权、业务引用、目标、规则、输入及能力要求。这样同一幂等键只有在业务
 * 参数完全一致时才能复用原执行。</p>
 */
@Component
public class ProbeDigestService {

    /**
     * 计算规范化探测请求的 SHA-256 指纹。
     *
     * @param command 探测命令
     * @return 64 位小写十六进制摘要
     */
    public String requestFingerprint(ProbeCommand command) {
        ExecutionContext context = command.context();
        StringBuilder canonical = new StringBuilder(1024);
        append(canonical, context.tenantId());
        append(canonical, context.actorId());
        append(canonical, context.activityId());
        append(canonical, context.taskId());
        append(canonical, context.runId());
        append(canonical, context.attemptId());
        append(canonical, context.authorizationRef());
        append(canonical, command.probeId());
        append(canonical, command.target().targetType().name());
        append(canonical, command.target().targetId());
        append(canonical, command.riskId());
        append(canonical, command.ruleSetVersion());
        append(canonical, command.executionMode().name());
        append(canonical, command.sessionRef());
        append(canonical, contentHash(command.input()));
        command.requiredCapabilities().stream()
                .map(Enum::name)
                .sorted()
                .forEach(value -> append(canonical, value));
        append(canonical, command.deadlineAt() == null ? null : command.deadlineAt().toString());
        return digest(canonical.toString());
    }

    /**
     * 计算证据正文的 SHA-256 摘要。
     *
     * @param content 原始正文
     * @return 64 位小写十六进制摘要
     */
    public String contentHash(String content) {
        return digest(content == null ? "" : content);
    }

    private void append(StringBuilder target, String value) {
        String normalized = value == null ? "<null>" : value;
        target.append(normalized.length()).append(':').append(normalized).append('|');
    }

    private String digest(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 是所有 Java 运行时必须提供的算法，发生该异常说明运行环境不可用。
            throw new IllegalStateException("当前Java运行环境不支持SHA-256", ex);
        }
    }
}
