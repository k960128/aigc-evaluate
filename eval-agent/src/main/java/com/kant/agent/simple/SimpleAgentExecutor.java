package com.kant.agent.simple;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 统一记录 Agent 调用及响应解析的执行结果。
 */
@Slf4j
final class SimpleAgentExecutor {

    private SimpleAgentExecutor() {
    }

    static <T> T execute(String type, String promptContent, AgentExecutionListener listener,
                         Supplier<ChatResponse> call, Function<String, T> converter) {
        String messageId = UUID.randomUUID().toString();
        listener.onStarted(messageId, type, promptContent);
        long startedAt = System.nanoTime();
        String contentResponse = null;
        Integer tokenCount = null;
        T result;
        try {
            ChatResponse response = call.get();
            if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                tokenCount = response.getMetadata().getUsage().getTotalTokens();
            }
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new IllegalStateException("Agent未返回有效响应");
            }
            contentResponse = response.getResult().getOutput().getText();
            if (contentResponse == null || contentResponse.isBlank()) {
                throw new IllegalStateException("Agent返回内容为空");
            }
            result = converter.apply(contentResponse);
            if (result == null) {
                throw new IllegalStateException("Agent响应解析结果为空");
            }
        } catch (RuntimeException exception) {
            int latency = elapsedMillis(startedAt);
            log.error("Agent执行失败，type={}, messageId={}, latency={}ms", type, messageId, latency, exception);
            String failureContent = contentResponse != null ? contentResponse
                    : exception.getClass().getSimpleName() + ": " + exception.getMessage();
            try {
                listener.onCompleted(messageId, failureContent, tokenCount, latency, false);
            } catch (RuntimeException logException) {
                log.error("保存Agent失败日志异常，type={}, messageId={}", type, messageId, logException);
                exception.addSuppressed(logException);
            }
            throw exception;
        }
        // 持久化失败直接向上抛出，避免将日志写入异常误判为模型调用异常。
        listener.onCompleted(messageId, contentResponse, tokenCount, elapsedMillis(startedAt), true);
        return result;
    }

    private static int elapsedMillis(long startedAt) {
        return (int) Math.min(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), Integer.MAX_VALUE);
    }
}
