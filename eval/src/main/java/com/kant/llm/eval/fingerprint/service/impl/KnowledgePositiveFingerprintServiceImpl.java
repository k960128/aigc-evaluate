package com.kant.llm.eval.fingerprint.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.fingerprint.dao.entity.KnowledgePositiveFingerprintDO;
import com.kant.llm.eval.fingerprint.dao.mapper.KnowledgePositiveFingerprintMapper;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgePositiveFingerprintPageRequest;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgePositiveFingerprintRequest;
import com.kant.llm.eval.fingerprint.dto.resp.KnowledgePositiveFingerprintVO;
import com.kant.llm.eval.fingerprint.engine.SimHashEngine;
import com.kant.llm.eval.fingerprint.service.KnowledgePositiveFingerprintService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class KnowledgePositiveFingerprintServiceImpl
        extends ServiceImpl<KnowledgePositiveFingerprintMapper, KnowledgePositiveFingerprintDO>
        implements KnowledgePositiveFingerprintService {

    private final SimHashEngine simHashEngine;
    private final StringRedisTemplate redisTemplate;
    // 工业界最佳实践：64 位分 4 块，允许最大容差为 3
    private static final int MAX_HAMMING_DISTANCE = 3;
    // 全局通用的风险分类标识
    private static final long GLOBAL_RISK_ID = 0L;
    // Redis 键前缀
    private static final String REDIS_KEY_PREFIX = "sim:hash:%d:block:%d:%d";

    public KnowledgePositiveFingerprintServiceImpl(SimHashEngine simHashEngine,
                                                   StringRedisTemplate redisTemplate) {
        this.simHashEngine = simHashEngine;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Boolean savePositiveFingerprint(KnowledgePositiveFingerprintRequest request) {
        KnowledgePositiveFingerprintDO fingerprintDO = new KnowledgePositiveFingerprintDO();
        fingerprintDO.setId(IdUtil.getSnowflakeNextId());
        fingerprintDO.setFingerprintText(request.getFingerprintText());
        fingerprintDO.setRiskDetailsId(request.getRiskDetailsId());
        fingerprintDO.setNormalizedFingerprintText(normalizeText(request.getFingerprintText()));
        fingerprintDO.setNormalizedHashValue(generateHash(normalizeText(request.getFingerprintText())));
        fingerprintDO.setCreator("admin");
        fingerprintDO.setCreator("admin");
        fingerprintDO.setCreateTime(LocalDateTime.now());
        fingerprintDO.setUpdateTime(LocalDateTime.now());
        fingerprintDO.setDeleted(false);
        save(fingerprintDO);
        return true;
    }

    @Override
    public Page<KnowledgePositiveFingerprintVO> pagePositiveFingerprint(KnowledgePositiveFingerprintPageRequest request) {
        LambdaQueryWrapper<KnowledgePositiveFingerprintDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(request.getFingerprintText())) {
            queryWrapper.like(KnowledgePositiveFingerprintDO::getFingerprintText, request.getFingerprintText());
        }
        if (ObjectUtil.isNotNull(request.getRiskDetailsId())) {
            queryWrapper.eq(KnowledgePositiveFingerprintDO::getRiskDetailsId, request.getRiskDetailsId());
        }

        Page<KnowledgePositiveFingerprintDO> pageResult = page(new Page<>(pageCurrent(request.getCurrent()),
                pageSize(request.getSize())), queryWrapper);
        Page<KnowledgePositiveFingerprintVO> voPage = new Page<>(request.getCurrent(), request.getSize(), pageResult.getTotal());
        if (CollectionUtil.isNotEmpty(pageResult.getRecords())) {
            voPage.setRecords(pageResult.getRecords().stream().map(fingerprint -> BeanUtil.copyProperties(fingerprint, KnowledgePositiveFingerprintVO.class)).collect(Collectors.toList()));
        }
        return voPage;
    }

    @Override
    public Boolean publishPositiveFingerprint() {
//        Map<Long, List<KnowledgePositiveFingerprintDO>> fingerprintMap = list()
//                .stream()
//                .collect(Collectors.groupingBy(KnowledgePositiveFingerprintDO::getRiskDetailsId));
//        log.info("开始同步正向指纹到Redis，共{}条", fingerprintMap.size());
//        fingerprintMap.forEach((riskDetailsId, fingerprints) -> {
//            if (CollectionUtil.isNotEmpty(fingerprints)) {
//                fingerprints.forEach(fingerprint -> {
//                    short[] blocks = splitToBlocks(fingerprint.getSimHashValue());
//                    // 使用 SessionCallback 替代底层的 RedisCallback，完美规避 sAdd 弃用问题
//                    redisTemplate.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
//                        @Override
//                        public <K, V> Object execute(org.springframework.data.redis.core.RedisOperations<K, V> operations) {
//                            // 强转为 StringRedisTemplate 的操作接口
//                            @SuppressWarnings("unchecked")
//                            var stringOperations = (org.springframework.data.redis.core.RedisOperations<String, String>) operations;
//
//                            for (int i = 0; i < 4; i++) {
//                                String key = String.format(REDIS_KEY_PREFIX, riskDetailsId, i, blocks[i]);
//                                // 直接使用高层 API，底层会自动通过 Pipeline 批量打包发送
//                                stringOperations.opsForSet().add(key, fingerprint.getSimHashValue().toString());
//                            }
//                            return null;
//                        }
//                    });
//                });
//            }
//        });
//        log.info("同步正向指纹到Redis完成");
        return true;
    }

    @Override
    public Boolean refreshPositiveFingerprintSimHash() {
        // 1. 获取所有正向指纹
//        List<KnowledgePositiveFingerprintDO> list = list();
//        list.forEach(fingerprint -> fingerprint.setSimHashValue(simHashEngine.compute(fingerprint.getFingerprintText())));
//        // 2. 批量更新数据库
//        updateBatchById(list);
        return true;
    }

    @Override
    public Boolean validateStandardRefusal(Long riskDetailsId, String checkText) {
        long currentHash = simHashEngine.compute(checkText);
        short[] blocks = splitToBlocks(currentHash);

        // Java 21 结构化并发：同时查询 全局(0) 和 当前分类(riskDetailsId) 的 Block
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // 组装 8 个并发获取任务 (4个全局抽屉 + 4个分类专属抽屉)
            List<StructuredTaskScope.Subtask<Set<String>>> tasks = Stream.concat(
                    createFetchTasks(scope, GLOBAL_RISK_ID, blocks),
                    createFetchTasks(scope, riskDetailsId, blocks)
            ).toList();

            // 挂起虚拟线程，等待所有 Redis 网络 IO 完成
            scope.join();
            scope.throwIfFailed();

            // 遍历所有候选集，校验真实海明距离
            for (var task : tasks) {
                Set<String> candidates = task.get();
                if (candidates == null || candidates.isEmpty()) {
                    continue;
                }

                for (String candidateStr : candidates) {
                    long candidateHash = Long.parseLong(candidateStr);
                    // 只要发现任意一个距离 <= 3 的案底，立刻判定为高度相似
                    if (simHashEngine.hammingDistance(currentHash, candidateHash) <= MAX_HAMMING_DISTANCE) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.error("异常：", e);
            return false;
        }

        return false;
    }

    @Override
    public void refreshFingerprintHash() {
        log.info("开始刷新正向指纹的Hash值和归一化");
        // 1. 获取所有正向指纹
        List<KnowledgePositiveFingerprintDO> list = list();
        list.forEach(fingerprint -> {
            fingerprint.setNormalizedFingerprintText(normalizeText(fingerprint.getFingerprintText()));
            fingerprint.setNormalizedHashValue(generateHash(fingerprint.getNormalizedFingerprintText()));
        });
        // 2. 批量更新数据库
        updateBatchById(list);
        log.info("刷新正向指纹的Hash值和归一化完成");
    }

    /**
     * 生成对应 riskId 的 4 个 Block 检索子任务
     */
    private Stream<StructuredTaskScope.Subtask<Set<String>>> createFetchTasks(
            StructuredTaskScope.ShutdownOnFailure scope, Long riskId, short[] blocks) {

        return java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> scope.fork(() -> fetchCandidatesFromRedis(riskId, i, blocks[i])));
    }

    private Set<String> fetchCandidatesFromRedis(Long riskId, int blockIndex, short blockValue) {
        // 全新的 Redis Key 设计，通过 riskId 隔绝不同小类的指纹冲突
        String key = String.format(REDIS_KEY_PREFIX, riskId, blockIndex, blockValue);
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members != null ? members : Collections.emptySet();
    }


    /**
     * 获取分页页码，未传时默认第 1 页。
     */
    private long pageCurrent(Integer current) {
        return Optional.ofNullable(current)
                .filter(value -> value > 0)
                .map(Integer::longValue)
                .orElse(1L);
    }

    /**
     * 获取分页大小，未传时默认每页 10 条。
     */
    private long pageSize(Integer size) {
        return Optional.ofNullable(size)
                .filter(value -> value > 0)
                .map(Integer::longValue)
                .orElse(10L);
    }

    /**
     * 位运算切片：将 64 bit 拆分为 4 个 16 bit 块
     */
    private short[] splitToBlocks(long hash) {
        short[] blocks = new short[4];
        blocks[0] = (short) (hash >>> 48);
        blocks[1] = (short) ((hash >>> 32) & 0xFFFF);
        blocks[2] = (short) ((hash >>> 16) & 0xFFFF);
        blocks[3] = (short) (hash & 0xFFFF);
        return blocks;
    }

    /**
     * 对违规内容进行归一化处理。
     * @param featureText 待归一化的字符串
     * @return 归一化后的字符串
     */
    private String normalizeText(String featureText) {
        String canonicalText = Normalizer.normalize(featureText, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder normalizedText = new StringBuilder(canonicalText.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < canonicalText.length(); ) {
            int codePoint = canonicalText.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (isWhitespace(codePoint)) {
                pendingSpace = normalizedText.length() > 0;
                continue;
            }
            int characterType = Character.getType(codePoint);
            if (characterType == Character.FORMAT || Character.isISOControl(codePoint)) {
                continue;
            }
            if (pendingSpace) {
                normalizedText.append(' ');
                pendingSpace = false;
            }
            normalizedText.appendCodePoint(codePoint);
        }
        return normalizedText.toString();
    }

    private boolean isWhitespace(int codePoint) {
        int characterType = Character.getType(codePoint);
        return Character.isWhitespace(codePoint)
                || characterType == Character.SPACE_SEPARATOR
                || characterType == Character.LINE_SEPARATOR
                || characterType == Character.PARAGRAPH_SEPARATOR;
    }

    /**
     * 对字符串进行 SHA-256 加密。
     *
     * @param value 待加密的字符串
     * @return 加密后的字符串
     */
    private String generateHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256 摘要算法", ex);
        }
    }
}
