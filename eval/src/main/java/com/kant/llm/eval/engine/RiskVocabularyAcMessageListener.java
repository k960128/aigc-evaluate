package com.kant.llm.eval.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.llm.eval.common.constant.RiskVocabularyAcRedisKeys;
import com.kant.llm.eval.engine.model.RiskVocabularyPublishMessage;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 风险词库 AC 版本发布消息监听器。
 *
 * <p>监听 Redis Pub/Sub 通知后，不在 Redis 监听线程里直接构建 AC，而是提交到本地单线程执行器。
 * 这样既避免阻塞 Redis 消息分发，也避免同一 JVM 同时构建多个 AC 版本造成内存尖峰。</p>
 */
@Slf4j
@Component
public class RiskVocabularyAcMessageListener implements MessageListener {

    /** 单个版本本地构建失败后的最大重试次数。 */
    private static final int MAX_RETRY_TIMES = 3;

    /** 固定退避时间，避免 Redis 或快照短暂异常时立刻连续打满重试。 */
    private static final long RETRY_BACKOFF_MILLIS = 3_000L;

    private final L1InterceptionEngine l1InterceptionEngine;

    private final ObjectMapper objectMapper;

    /**
     * 单线程 AC 构建执行器。
     *
     * <p>同一节点内串行构建，保证内存里不会同时出现多棵正在构建的新 Trie。</p>
     */
    private final ExecutorService buildExecutor;

    public RiskVocabularyAcMessageListener(L1InterceptionEngine l1InterceptionEngine,
                                           ObjectMapper objectMapper,
                                           RedisMessageListenerContainer listenerContainer) {
        this.l1InterceptionEngine = l1InterceptionEngine;
        this.objectMapper = objectMapper;
        this.buildExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("risk-vocabulary-ac-builder");
            thread.setDaemon(true);
            return thread;
        });
        listenerContainer.addMessageListener(this, new ChannelTopic(RiskVocabularyAcRedisKeys.CHANNEL));
    }

    /**
     * Redis Pub/Sub 回调入口。
     *
     * <p>这里只做消息反序列化和任务提交，真正构建在 buildExecutor 中执行。</p>
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        RiskVocabularyPublishMessage publishMessage;
        try {
            publishMessage = objectMapper.readValue(body, RiskVocabularyPublishMessage.class);
        } catch (JsonProcessingException ex) {
            log.warn("忽略无效的 AC 自动机发布消息：{}", body, ex);
            return;
        }
        buildExecutor.submit(() -> rebuildWithRetry(publishMessage));
    }

    /**
     * 应用关闭时停止构建线程，避免线程泄漏。
     */
    @PreDestroy
    public void destroy() {
        buildExecutor.shutdown();
        try {
            if (!buildExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                buildExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            buildExecutor.shutdownNow();
        }
    }

    /**
     * 对同一个版本做有限次数重试。
     *
     * <p>构建失败只影响当前节点，不修改 DB 的 syncStatus，也不通知其他节点回滚。</p>
     */
    private void rebuildWithRetry(RiskVocabularyPublishMessage message) {
        for (int attempt = 1; attempt <= MAX_RETRY_TIMES; attempt++) {
            try {
                boolean success = l1InterceptionEngine.rebuildFromPublishMessage(message);
                if (success) {
                    return;
                }
            } catch (Exception ex) {
                log.warn("构建 AC 自动机失败，版本号={}，重试次数={}/{}",
                        message.getVersionId(), attempt, MAX_RETRY_TIMES, ex);
            }

            if (attempt < MAX_RETRY_TIMES) {
                sleepBeforeRetry(message, attempt);
            }
        }
        log.error("构建 AC 自动机重试次数已耗尽，版本号={}，快照哈希={}",
                message.getVersionId(), message.getHash());
    }

    /**
     * 重试前等待一小段时间，给 Redis 快照读取或网络抖动恢复的机会。
     */
    private void sleepBeforeRetry(RiskVocabularyPublishMessage message, int attempt) {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("AC 自动机构建重试等待被中断，版本号={}，重试次数={}", message.getVersionId(), attempt);
        }
    }
}
