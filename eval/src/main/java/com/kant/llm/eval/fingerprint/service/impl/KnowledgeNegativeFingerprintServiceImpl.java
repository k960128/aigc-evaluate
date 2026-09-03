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

import java.time.LocalDateTime;
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
        fingerprintDO.setKeyword(request.getKeyword());
        fingerprintDO.setMatchType(request.getMatchType());
        fingerprintDO.setRiskLevel(1);
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
        if (StringUtils.isNotBlank(request.getKeyword())) {
            queryWrapper.like(KnowledgeNegativeFingerprintDO::getKeyword, request.getKeyword());
        }
        if (ObjectUtil.isNotNull(request.getRiskDetailsId())) {
            queryWrapper.eq(KnowledgeNegativeFingerprintDO::getRiskDetailsId, request.getRiskDetailsId());
        }
        if (ObjectUtil.isNotNull(request.getMatchType())) {
            queryWrapper.eq(KnowledgeNegativeFingerprintDO::getMatchType, request.getMatchType());
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
