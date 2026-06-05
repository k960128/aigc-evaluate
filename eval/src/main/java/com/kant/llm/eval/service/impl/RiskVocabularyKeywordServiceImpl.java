package com.kant.llm.eval.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.llm.eval.common.constant.RiskVocabularyAcRedisKeys;
import com.kant.llm.eval.common.errorcode.BaseErrorCode;
import com.kant.llm.eval.common.exception.ServiceException;
import com.kant.llm.eval.dao.entity.RiskVocabularyKeywordDO;
import com.kant.llm.eval.dao.mapper.RiskVocabularyKeywordMapper;
import com.kant.llm.eval.engine.model.RiskVocabularyPublishMessage;
import com.kant.llm.eval.engine.model.RiskVocabularySnapshot;
import com.kant.llm.eval.engine.model.RiskVocabularySnapshotItem;
import com.kant.llm.eval.engine.support.RiskVocabularyAcSupport;
import com.kant.llm.eval.service.RiskVocabularyKeywordService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 风险词库特征词服务实现。
 *
 * <p>本类负责把当前数据库中的全量有效特征词发布成 Redis 版本快照，并通过 Redis Pub/Sub
 * 通知集群节点异步构建 AC 自动机。</p>
 */
@Slf4j
@Service
public class RiskVocabularyKeywordServiceImpl extends ServiceImpl<RiskVocabularyKeywordMapper, RiskVocabularyKeywordDO> implements RiskVocabularyKeywordService {

    /**
     * syncStatus=true 表示词条已经进入某次发布快照。
     *
     * <p>它不表示所有 JVM 节点都已经构建成功；节点构建失败只影响该节点本地 AC，不回滚业务词条状态。</p>
     */
    private static final boolean SYNC_STATUS_SYNCED = true;

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    private final RedissonClient redissonClient;

    public RiskVocabularyKeywordServiceImpl(StringRedisTemplate stringRedisTemplate,
                                            ObjectMapper objectMapper,
                                            RedissonClient redissonClient) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.redissonClient = redissonClient;
    }

    /**
     * 发布一个新的 AC 自动机特征词版本。
     *
     * <p>流程：读取 DB 全量未删除词条 -> 生成稳定快照和 hash -> Redis 原子写入快照、latest 指针和通知
     * -> 发布成功后把未删除词条标记为已进入发布快照。</p>
     */
    @Override
    public String publishAcSnapshot() {
        RLock lock = redissonClient.getLock(RiskVocabularyAcRedisKeys.PUBLISH_LOCK);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 5, TimeUnit.MINUTES);
            if (!locked) {
                return "当前已有特征词版本正在发布，请稍后重试";
            }

            // 每次发布都使用全量未删除词条生成快照，避免增量补丁失败后难以恢复。
            List<RiskVocabularyKeywordDO> keywords = lambdaQuery()
                    .eq(RiskVocabularyKeywordDO::getDeleted, false)
                    .list();
            List<RiskVocabularySnapshotItem> snapshotItems = RiskVocabularyAcSupport.toSnapshotItems(keywords);
            String snapshotHash = RiskVocabularyAcSupport.calculateHash(snapshotItems);
            String latestHash = stringRedisTemplate.opsForValue().get(RiskVocabularyAcRedisKeys.LATEST_HASH);
            if (Objects.equals(snapshotHash, latestHash)) {
                // 内容未变化时不产生新版本，但可以把业务上的待同步状态收敛为已同步。
                markUndeletedKeywordsSynced();
                return "特征词内容未变化，无需重复发布 AC 自动机版本";
            }

            Long versionId = IdUtil.getSnowflake().nextId();
            LocalDateTime publishTime = LocalDateTime.now();
            RiskVocabularySnapshot snapshot = RiskVocabularySnapshot.builder()
                    .versionId(versionId)
                    .hash(snapshotHash)
                    .wordCount(snapshotItems.size())
                    .publishTime(publishTime)
                    .items(snapshotItems)
                    .build();
            String snapshotKey = RiskVocabularyAcRedisKeys.snapshotKey(versionId);
            String snapshotJson = toJson(snapshot);

            RiskVocabularyPublishMessage message = RiskVocabularyPublishMessage.builder()
                    .versionId(versionId)
                    .snapshotKey(snapshotKey)
                    .hash(snapshotHash)
                    .wordCount(snapshotItems.size())
                    .publishTime(publishTime)
                    .build();
            // Redis 发布失败会抛异常，下面的 syncStatus 更新不会执行。
            publishSnapshotToRedis(snapshotKey, snapshotJson, versionId, snapshotHash, toJson(message));
            markUndeletedKeywordsSynced();

            log.info("AC 自动机特征词版本发布完成，版本号={}，特征词数量={}，快照哈希={}",
                    versionId, snapshotItems.size(), snapshotHash);
            return "成功发布 AC 自动机特征词版本 " + versionId + "，特征词数量 " + snapshotItems.size();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException("发布 AC 自动机特征词版本被中断", ex, BaseErrorCode.SERVICE_ERROR);
        } catch (Exception ex) {
            throw new ServiceException("发布 AC 自动机特征词版本失败：" + ex.getMessage(), ex, BaseErrorCode.SERVICE_ERROR);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 将当前未删除词条标记为“已进入发布快照”。
     */
    private void markUndeletedKeywordsSynced() {
        RiskVocabularyKeywordDO updateEntity = new RiskVocabularyKeywordDO();
        updateEntity.setSyncStatus(SYNC_STATUS_SYNCED);
        update(updateEntity, new LambdaQueryWrapper<RiskVocabularyKeywordDO>()
                .eq(RiskVocabularyKeywordDO::getDeleted, false));
    }

    /**
     * 使用 Redis MULTI/EXEC 写入快照、latest 指针并发送通知。
     *
     * <p>这里的事务只保证 Redis 侧命令作为一个批次提交；DB 状态更新在 Redis 发布成功后再执行。</p>
     */
    private void publishSnapshotToRedis(String snapshotKey,
                                        String snapshotJson,
                                        Long versionId,
                                        String snapshotHash,
                                        String messageJson) {
        List<Object> results = stringRedisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public List<Object> execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForValue().set(snapshotKey, snapshotJson);
                operations.opsForValue().set(RiskVocabularyAcRedisKeys.LATEST_VERSION, String.valueOf(versionId));
                operations.opsForValue().set(RiskVocabularyAcRedisKeys.LATEST_HASH, snapshotHash);
                operations.convertAndSend(RiskVocabularyAcRedisKeys.CHANNEL, messageJson);
                return operations.exec();
            }
        });
        if (results == null || results.size() < 4) {
            throw new ServiceException("发布 AC 自动机特征词版本的 Redis 事务执行失败", BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 统一将快照对象和发布消息序列化为 JSON 字符串。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ServiceException("序列化 AC 自动机特征词快照失败", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
