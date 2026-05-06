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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class AcAutomatonService {

    private static final String LEGACY_REDIS_KEY = "llm_sec:risk:items";
    private static final String REDIS_ITEM_KEYS_PREFIX = "llm_sec:risk:item_keys:";
    private static final String REDIS_CURRENT_VERSION_KEY = "llm_sec:risk:current_version";
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
        log.info("Initialize AC automaton service");

        loadFromRedis();

        registerListener();
    }

    private void loadFromRedis() {
        virtualExecutor.submit(() -> {
            log.info("Start loading risk vocabularies from Redis to build AC automaton");
            long startTime = System.currentTimeMillis();

            try {
                RiskKeywordSnapshot snapshot = loadRiskKeywordSnapshot();

                AhoCorasickDoubleArrayTrie<String> newTrie = new AhoCorasickDoubleArrayTrie<>();
                newTrie.build(snapshot.keywordMap());

                acTrieRef.set(newTrie);

                log.info("AC automaton built, loaded {} exact-match vocabularies, cost={}ms",
                        snapshot.count(), System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                log.error("Build AC automaton from Redis failed", e);
            }
        });
    }

    private RiskKeywordSnapshot loadRiskKeywordSnapshot() {
        String version = currentVersion();
        if (version == null || version.isBlank()) {
            log.info("Risk vocabulary version is empty, fallback to legacy Redis key");
            return loadKeywordSnapshotFromHash(LEGACY_REDIS_KEY);
        }

        String itemKeysKey = itemKeysKey(version);
        Set<Object> itemKeys = redisTemplate.opsForSet().members(itemKeysKey);
        if (itemKeys == null || itemKeys.isEmpty()) {
            log.warn("Risk vocabulary item key index is empty, version={}", version);
            return new RiskKeywordSnapshot(new TreeMap<>(), 0);
        }

        TreeMap<String, String> keywordMap = new TreeMap<>();
        int count = 0;
        for (Object itemKey : itemKeys) {
            count += putExactMatchKeywords(String.valueOf(itemKey), keywordMap);
        }

        return new RiskKeywordSnapshot(keywordMap, count);
    }

    private RiskKeywordSnapshot loadKeywordSnapshotFromHash(String redisKey) {
        TreeMap<String, String> keywordMap = new TreeMap<>();
        int count = putExactMatchKeywords(redisKey, keywordMap);
        return new RiskKeywordSnapshot(keywordMap, count);
    }

    private int putExactMatchKeywords(String redisKey, TreeMap<String, String> keywordMap) {
        Map<Object, Object> hashEntries = redisTemplate.opsForHash().entries(redisKey);
        int count = 0;
        for (Object json : hashEntries.values()) {
            RiskVocabularyDO vocabulary = JSON.parseObject(String.valueOf(json), RiskVocabularyDO.class);
            if (!isExactMatchVocabulary(vocabulary)) {
                continue;
            }
            keywordMap.putIfAbsent(vocabulary.getKeyword(), vocabulary.getKeyword());
            count++;
        }
        return count;
    }

    private boolean isExactMatchVocabulary(RiskVocabularyDO vocabulary) {
        return vocabulary != null
                && vocabulary.getMatchType() != null
                && vocabulary.getMatchType() == 1
                && vocabulary.getKeyword() != null
                && !vocabulary.getKeyword().isBlank();
    }

    private String currentVersion() {
        Object version = redisTemplate.opsForValue().get(REDIS_CURRENT_VERSION_KEY);
        return version == null ? null : String.valueOf(version);
    }

    private String itemKeysKey(String version) {
        return REDIS_ITEM_KEYS_PREFIX + version;
    }

    private void registerListener() {
        listenerContainer.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            log.info("Received Redis risk vocabulary update notification: {}", body);
            loadFromRedis();
        }, new ChannelTopic(REDIS_CHANNEL));

        log.info("Registered Redis Pub/Sub listener, channel={}", REDIS_CHANNEL);
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
            log.error("Get AC automaton hit value failed", e);
            return null;
        }
    }

    private record RiskKeywordSnapshot(TreeMap<String, String> keywordMap, int count) {
    }
}
