package com.kant.llm.eval.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.llm.eval.dao.entity.RiskVocabularyDO;
import com.kant.llm.eval.dao.mapper.RiskVocabularyMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RiskSyncService {

    private static final String REDIS_KEY = "llm_sec:risk:items";
    private static final String REDIS_CHANNEL = "llm_sec:risk:notify";

    @Resource
    private RiskVocabularyMapper riskVocabularyMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    public void syncAll() {
        long startTime = System.currentTimeMillis();
        log.info("开始全量同步风险词汇到 Redis");

        try {
            LambdaQueryWrapper<RiskVocabularyDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RiskVocabularyDO::getStatus, true)
                   .eq(RiskVocabularyDO::getDeleted, false);
            List<RiskVocabularyDO> vocabularies = riskVocabularyMapper.selectList(wrapper);

            Map<String, String> hashData = vocabularies.stream()
                    .collect(Collectors.toMap(
                            v -> String.valueOf(v.getId()),
                            JSON::toJSONString
                    ));

            redisTemplate.delete(REDIS_KEY);
            if (!hashData.isEmpty()) {
                redisTemplate.opsForHash().putAll(REDIS_KEY, hashData);
            }

            publishNotify();

            log.info("全量同步完成，共同步 {} 条数据，耗时 {}ms", hashData.size(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("全量同步风险词汇失败", e);
            throw new RuntimeException("全量同步风险词汇失败", e);
        }
    }

    private void publishNotify() {
        try {
            redisTemplate.convertAndSend(REDIS_CHANNEL, "UPDATE");
            log.info("已向频道 {} 发布更新通知", REDIS_CHANNEL);
        } catch (Exception e) {
            log.error("发布 Redis 通知失败", e);
        }
    }
}
