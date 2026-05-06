package com.kant.llm.eval.service;

import com.alibaba.fastjson2.JSON;
import com.hankcs.hanlp.collection.AhoCorasick.AhoCorasickDoubleArrayTrie;
import com.kant.llm.eval.dao.entity.RiskVocabularyDO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AcAutomatonService {

    private static final String REDIS_KEY = "llm_sec:risk:items";
    private static final String REDIS_CHANNEL = "llm_sec:risk:notify";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ExecutorService virtualExecutor;

    private final AtomicReference<AhoCorasickDoubleArrayTrie<String>> acTrieRef =
            new AtomicReference<>(new AhoCorasickDoubleArrayTrie<>());

    public AcAutomatonService(RedisTemplate<String, Object> redisTemplate,
                              RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PostConstruct
    public void init() {
        log.info("初始化 AC 自动机服务");

        loadFromRedis();

        registerListener();
    }

    private void loadFromRedis() {
        virtualExecutor.submit(() -> {
            log.info("开始从 Redis 加载风险词汇构建 AC 自动机");
            long startTime = System.currentTimeMillis();

            try {
                Map<Object, Object> hashEntries = redisTemplate.opsForHash().entries(REDIS_KEY);

                List<RiskVocabularyDO> vocabularies = hashEntries.values().stream()
                        .map(json -> JSON.parseObject(String.valueOf(json), RiskVocabularyDO.class))
                        .filter(v -> v.getMatchType() != null && v.getMatchType() == 1)
                        .collect(Collectors.toList());

                AhoCorasickDoubleArrayTrie<String> newTrie = new AhoCorasickDoubleArrayTrie<>();
                TreeMap<String, String> keywordMap = vocabularies.stream()
                        .collect(Collectors.toMap(
                                RiskVocabularyDO::getKeyword,
                                RiskVocabularyDO::getKeyword,
                                (a, b) -> a,
                                TreeMap::new
                        ));
                newTrie.build(keywordMap);

                acTrieRef.set(newTrie);

                log.info("AC 自动机构建完成，加载 {} 条精确匹配词汇，耗时 {}ms",
                        vocabularies.size(), System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                log.error("从 Redis 加载数据构建 AC 自动机失败", e);
            }
        });
    }

    private void registerListener() {
        listenerContainer.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody());
            log.info("收到 Redis 更新通知: {}", body);
            loadFromRedis();
        }, new ChannelTopic(REDIS_CHANNEL));

        log.info("已注册 Redis Pub/Sub 监听器，监听频道: {}", REDIS_CHANNEL);
    }

    @SuppressWarnings("rawtypes")
    public String match(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        AhoCorasickDoubleArrayTrie<String> trie = acTrieRef.get();

        List hits = trie.parseText(text);

        if (!hits.isEmpty()) {
            Object hit = hits.get(0);
            return (String) getValueFromHit(hit);
        }

        return null;
    }

    @SuppressWarnings("rawtypes")
    private Object getValueFromHit(Object hit) {
        try {
            java.lang.reflect.Field valueField = hit.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            return valueField.get(hit);
        } catch (Exception e) {
            log.error("反射获取 Hit.value 失败", e);
            return null;
        }
    }
}
