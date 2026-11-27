package com.kant.llm.eval.fingerprint.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.fingerprint.dao.entity.KnowledgeNegativeFingerprintDO;
import com.kant.llm.eval.fingerprint.dao.mapper.KnowledgeNegativeFingerprintMapper;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgeNegativeFingerprintPageRequest;
import com.kant.llm.eval.fingerprint.dto.req.KnowledgeNegativeFingerprintRequest;
import com.kant.llm.eval.fingerprint.dto.resp.KnowledgeNegativeFingerprintVO;
import com.kant.llm.eval.fingerprint.service.KnowledgeNegativeFingerprintService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeNegativeFingerprintServiceImpl
        extends ServiceImpl<KnowledgeNegativeFingerprintMapper, KnowledgeNegativeFingerprintDO>
        implements KnowledgeNegativeFingerprintService {

    @Override
    public Boolean saveNegativeFingerprint(KnowledgeNegativeFingerprintRequest request) {
        KnowledgeNegativeFingerprintDO fingerprintDO = new KnowledgeNegativeFingerprintDO();
        fingerprintDO.setId(IdUtil.getSnowflakeNextId());
        fingerprintDO.setRiskDetailsId(request.getRiskDetailsId());
        fingerprintDO.setFingerprintText(request.getFingerprintText());
        fingerprintDO.setNormalizedFingerprintText(normalizeText(request.getFingerprintText()));
        fingerprintDO.setNormalizedHashValue(generateHash(request.getFingerprintText()));
        fingerprintDO.setCreator("admin");
        fingerprintDO.setUpdater("admin");
        fingerprintDO.setCreateTime(LocalDateTime.now());
        fingerprintDO.setUpdateTime(LocalDateTime.now());
        fingerprintDO.setDeleted(false);
        save(fingerprintDO);
        return true;
    }

    @Override
    public Page<KnowledgeNegativeFingerprintVO> pageNegativeFingerprint(KnowledgeNegativeFingerprintPageRequest request) {
        LambdaQueryWrapper<KnowledgeNegativeFingerprintDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(request.getFingerprintText())) {
            queryWrapper.like(KnowledgeNegativeFingerprintDO::getFingerprintText, request.getFingerprintText());
        }
        if (ObjectUtil.isNotNull(request.getRiskDetailsId())) {
            queryWrapper.eq(KnowledgeNegativeFingerprintDO::getRiskDetailsId, request.getRiskDetailsId());
        }

        Page<KnowledgeNegativeFingerprintDO> pageResult = page(new Page<>(pageCurrent(request.getCurrent()),
                pageSize(request.getSize())), queryWrapper);

        Page<KnowledgeNegativeFingerprintVO> voPage = new Page<>(request.getCurrent(), request.getSize(), pageResult.getTotal());
        if (CollectionUtil.isNotEmpty(pageResult.getRecords())) {
            voPage.setRecords(pageResult.getRecords()
                    .stream()
                    .map(fingerprint -> BeanUtil.copyProperties(fingerprint, KnowledgeNegativeFingerprintVO.class))
                    .collect(Collectors.toList()));
        }
        return voPage;
    }

    @Override
    public Boolean publishNegativeFingerprint() {
        return true;
    }

    @Override
    public void refreshFingerprintHash() {
        log.info("开始刷新负向指纹的Hash值和归一化");
        // 1. 获取所有负向指纹
        List<KnowledgeNegativeFingerprintDO> list = list();
        list.forEach(fingerprint -> {
            fingerprint.setNormalizedFingerprintText(normalizeText(fingerprint.getFingerprintText()));
            fingerprint.setNormalizedHashValue(generateHash(fingerprint.getNormalizedFingerprintText()));
        });
        // 2. 批量更新数据库
        updateBatchById(list);
        log.info("刷新负向指纹的Hash值和归一化完成");
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
