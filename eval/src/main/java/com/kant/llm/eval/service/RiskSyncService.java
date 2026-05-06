package com.kant.llm.eval.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.llm.eval.dao.entity.RiskVocabularyDO;
import com.kant.llm.eval.dao.mapper.RiskVocabularyMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RiskSyncService {

    private static final String LEGACY_REDIS_KEY = "llm_sec:risk:items";
    private static final String REDIS_KEY_PREFIX = "llm_sec:risk:items:";
    private static final String REDIS_ITEM_KEYS_PREFIX = "llm_sec:risk:item_keys:";
    private static final String REDIS_CURRENT_VERSION_KEY = "llm_sec:risk:current_version";
    private static final String REDIS_CHANNEL = "llm_sec:risk:notify";
    private static final String REDIS_SYNC_LOCK_KEY = "llm_sec:risk:sync_lock";
    private static final Duration REDIS_SYNC_LOCK_TTL = Duration.ofMinutes(30);
    private static final long PAGE_SIZE = 5000L;

    @Resource
    private RiskVocabularyMapper riskVocabularyMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    public void syncAll() {
        long startTime = System.currentTimeMillis();
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(REDIS_SYNC_LOCK_KEY, lockToken, REDIS_SYNC_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("Risk vocabulary sync is already running, skip this round");
            return;
        }

        String newVersion = String.valueOf(System.currentTimeMillis());
        String oldVersion = currentVersion();
        log.info("Start full risk vocabulary sync to Redis, version={}", newVersion);

        try {
            long total = syncByPage(newVersion);

            redisTemplate.opsForValue().set(REDIS_CURRENT_VERSION_KEY, newVersion);
            publishNotify(newVersion);

            cleanupVersion(oldVersion);
            redisTemplate.delete(LEGACY_REDIS_KEY);

            log.info("Full risk vocabulary sync finished, version={}, total={}, cost={}ms",
                    newVersion, total, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            cleanupVersion(newVersion);
            log.error("Full risk vocabulary sync failed, version={}", newVersion, e);
            throw new RuntimeException("Full risk vocabulary sync failed", e);
        } finally {
            releaseLock(lockToken);
        }
    }

    private long syncByPage(String version) {
        long pageNo = 1L;
        long total = 0L;

        while (true) {
            LambdaQueryWrapper<RiskVocabularyDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RiskVocabularyDO::getStatus, true)
                    .eq(RiskVocabularyDO::getDeleted, false)
                    .orderByAsc(RiskVocabularyDO::getId);

            Page<RiskVocabularyDO> page = new Page<>(pageNo, PAGE_SIZE, false);
            List<RiskVocabularyDO> records = riskVocabularyMapper.selectPage(page, wrapper).getRecords();
            if (records.isEmpty()) {
                break;
            }

            writePage(version, records);
            total += records.size();

            if (records.size() < PAGE_SIZE) {
                break;
            }
            pageNo++;
        }

        return total;
    }

    private void writePage(String version, List<RiskVocabularyDO> vocabularies) {
        Map<Long, Map<String, String>> groupedByItemId = new HashMap<>();
        for (RiskVocabularyDO vocabulary : vocabularies) {
            Long itemId = vocabulary.getItemId();
            if (itemId == null) {
                log.warn("Skip risk vocabulary without itemId, id={}", vocabulary.getId());
                continue;
            }
            groupedByItemId.computeIfAbsent(itemId, ignored -> new HashMap<>())
                    .put(String.valueOf(vocabulary.getId()), JSON.toJSONString(vocabulary));
        }

        if (groupedByItemId.isEmpty()) {
            return;
        }

        Set<String> itemKeys = groupedByItemId.keySet().stream()
                .map(itemId -> itemKey(version, itemId))
                .collect(Collectors.toSet());
        redisTemplate.opsForSet().add(itemKeysKey(version), itemKeys.toArray());

        groupedByItemId.forEach((itemId, hashData) -> redisTemplate.opsForHash()
                .putAll(itemKey(version, itemId), hashData));
    }

    private String currentVersion() {
        Object version = redisTemplate.opsForValue().get(REDIS_CURRENT_VERSION_KEY);
        return version == null ? null : String.valueOf(version);
    }

    private String itemKeysKey(String version) {
        return REDIS_ITEM_KEYS_PREFIX + version;
    }

    private String itemKey(String version, Long itemId) {
        return REDIS_KEY_PREFIX + version + ":" + itemId;
    }

    private void cleanupVersion(String version) {
        if (version == null || version.isBlank()) {
            return;
        }

        String itemKeysKey = itemKeysKey(version);
        Set<Object> itemKeys = redisTemplate.opsForSet().members(itemKeysKey);
        if (itemKeys != null && !itemKeys.isEmpty()) {
            Collection<String> redisKeys = itemKeys.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.toSet());
            redisTemplate.delete(redisKeys);
        }
        redisTemplate.delete(itemKeysKey);
    }

    private void releaseLock(String lockToken) {
        try {
            Object cachedToken = redisTemplate.opsForValue().get(REDIS_SYNC_LOCK_KEY);
            if (lockToken.equals(String.valueOf(cachedToken))) {
                redisTemplate.delete(REDIS_SYNC_LOCK_KEY);
            }
        } catch (Exception e) {
            log.warn("Release risk vocabulary sync lock failed", e);
        }
    }

    private void publishNotify(String version) {
        try {
            redisTemplate.convertAndSend(REDIS_CHANNEL, version);
            log.info("Published risk vocabulary update notification, channel={}, version={}", REDIS_CHANNEL, version);
        } catch (Exception e) {
            log.error("Publish Redis risk vocabulary notification failed", e);
        }
    }
}
