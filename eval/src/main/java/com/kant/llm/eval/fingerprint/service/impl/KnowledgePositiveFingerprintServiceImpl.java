package com.kant.llm.eval.fingerprint.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.hash.Hashing;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.kant.llm.eval.fingerprint.dao.entity.KnowledgePositiveFingerprintDO;
import com.kant.llm.eval.fingerprint.dao.mapper.KnowledgePositiveFingerprintMapper;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgePositiveFingerprintPageRequest;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgePositiveFingerprintRequest;
import com.kant.llm.eval.fingerprint.dto.resp.KnowledgePositiveFingerprintVO;
import com.kant.llm.eval.fingerprint.service.KnowledgePositiveFingerprintService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgePositiveFingerprintServiceImpl
        extends ServiceImpl<KnowledgePositiveFingerprintMapper, KnowledgePositiveFingerprintDO>
        implements KnowledgePositiveFingerprintService {


    @Override
    public Boolean savePositiveFingerprint(KnowledgePositiveFingerprintRequest request) {
        KnowledgePositiveFingerprintDO fingerprintDO = new KnowledgePositiveFingerprintDO();
        fingerprintDO.setId(IdUtil.getSnowflakeNextId());
        fingerprintDO.setFingerprintText(request.getFingerprintText());
        fingerprintDO.setRiskDetailsId(request.getRiskDetailsId());
        fingerprintDO.setSimHashValue(compute(request.getFingerprintText()));
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


    /**
     * 计算文本的 64 位 SimHash 指纹
     */
    private Long compute(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0L;
        }

        int[] v = new int[64];
        // 1. 工业级中文分词 (过滤停用词、标点)
        List<Term> terms = HanLP.segment(text);

        for (Term term : terms) {
            String word = term.word;
            // 过滤单字无意义标点或极短助词
            if (word.length() < 2 && !Character.isLetterOrDigit(word.charAt(0))) {
                continue;
            }

            // 2. 使用 Guava 的 MurmurHash3 算法生成 64 位哈希
            long wordHash = Hashing.murmur3_128()
                    .hashString(word, StandardCharsets.UTF_8)
                    .asLong();

            // 3. 动态加权：词越长（或可结合 TF-IDF），对特征的偏转影响越大
            int weight = word.length();

            // 4. 降维合并 (位运算)
            for (int i = 0; i < 64; i++) {
                if (((wordHash >>> i) & 1) == 1) {
                    v[i] += weight;
                } else {
                    v[i] -= weight;
                }
            }
        }

        // 5. 降维并生成最终的 64 位指纹
        long fingerprint = 0L;
        for (int i = 0; i < 64; i++) {
            if (v[i] > 0) {
                fingerprint |= (1L << i);
            }
        }
        return fingerprint;
    }

    /**
     * 计算海明距离 (异或后统计二进制 1 的个数)
     */
    public int hammingDistance(long hash1, long hash2) {
        return Long.bitCount(hash1 ^ hash2);
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
}
