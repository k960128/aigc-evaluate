package com.kant.llm.eval.knowledge.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kant.framework.exception.ServiceException;
import com.kant.framework.mq.producer.MessageQueueProducer;
import com.kant.framework.mq.producer.MessageSendRequest;
import com.kant.llm.common.enums.KbSyncStatusEnums;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentDO;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentVersionDO;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentVersionSnapshotDO;
import com.kant.llm.eval.knowledge.dto.req.KnowledgeFeatureDocumentPageReq;
import com.kant.llm.eval.knowledge.dto.req.KnowledgeFeatureDocumentReq;
import com.kant.llm.eval.knowledge.dto.req.KnowledgeFeatureVersionReq;
import com.kant.llm.eval.knowledge.dto.req.PublishVersionReq;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVO;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVersionSnapshotVO;
import com.kant.llm.eval.knowledge.dto.resp.KnowledgeFeatureDocumentVersionVO;
import com.kant.llm.eval.knowledge.mq.v2.message.DocumentPublishVersionMessage;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentService;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentVersionService;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentVersionSnapshotService;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureManagerService;
import com.kant.llm.eval.risk.dao.entity.RiskDetailsDO;
import com.kant.llm.eval.risk.service.RiskDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.kant.llm.common.constant.EvalMqTopics.KNOWLEDGE_PUBLISH_VERSION_TOPIC;

/**
 * 攻击语义知识库聚合管理Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeFeatureManagerServiceImpl implements KnowledgeFeatureManagerService {

    // ======================================
    private final KnowledgeFeatureDocumentService knowledgeFeatureDocumentService;
    private final KnowledgeFeatureDocumentVersionService knowledgeFeatureDocumentVersionService;
    private final KnowledgeFeatureDocumentVersionSnapshotService knowledgeFeatureDocumentVersionSnapshotService;
    private final RiskDetailsService riskDetailsService;
    private final MessageQueueProducer messageQueueProducer;
    private final RedisTemplate<String, Object> redisTemplate;
    // ======================================

    @Override
    public KnowledgeFeatureDocumentVersionVO getKnowledgeFeatureDocumentVersion(KnowledgeFeatureVersionReq req) {
        KnowledgeFeatureDocumentVersionDO knowledgeFeatureDocumentVersionDO =
                knowledgeFeatureDocumentVersionService.getOne(
                        new LambdaQueryWrapper<KnowledgeFeatureDocumentVersionDO>()
                                .eq(KnowledgeFeatureDocumentVersionDO::getRiskDetailsId, req.getRiskDetailsId()));
        if (knowledgeFeatureDocumentVersionDO == null) {
            return null;
        }
        return BeanUtil.copyProperties(knowledgeFeatureDocumentVersionDO, KnowledgeFeatureDocumentVersionVO.class);
    }

    @Override
    public List<KnowledgeFeatureDocumentVersionSnapshotVO> getKnowledgeFeatureDocumentVersionSnapshotList(KnowledgeFeatureVersionReq req) {
        List<KnowledgeFeatureDocumentVersionSnapshotDO> list = knowledgeFeatureDocumentVersionSnapshotService.list(
                new LambdaQueryWrapper<KnowledgeFeatureDocumentVersionSnapshotDO>()
                        .eq(KnowledgeFeatureDocumentVersionSnapshotDO::getRiskDetailsId, req.getRiskDetailsId()));
        if (CollectionUtil.isEmpty(list)) {
            return CollectionUtil.newArrayList();
        }
        return BeanUtil.copyToList(list, KnowledgeFeatureDocumentVersionSnapshotVO.class);
    }

    @Override
    public Page<KnowledgeFeatureDocumentVO> getKnowledgeFeatureDocumentPage(KnowledgeFeatureDocumentPageReq pageReq) {
        KnowledgeFeatureDocumentPageReq queryRequest = pageReq == null
                ? new KnowledgeFeatureDocumentPageReq()
                : pageReq;
        LambdaQueryWrapper<KnowledgeFeatureDocumentDO> queryWrapper = new LambdaQueryWrapper<>();
        if (queryRequest.getRiskDetailsId() != null) {
            queryWrapper.eq(KnowledgeFeatureDocumentDO::getRiskDetailsId, queryRequest.getRiskDetailsId());
        }
        if (queryRequest.getCurrentVersionId() != null) {
            queryWrapper.and(wrapper -> wrapper.eq(KnowledgeFeatureDocumentDO::getCurrentVersionId, queryRequest.getCurrentVersionId())
                    .or()
                    .isNull(KnowledgeFeatureDocumentDO::getCurrentVersionId));
        }
        if (queryRequest.getSyncStatus() != null) {
            queryWrapper.eq(KnowledgeFeatureDocumentDO::getSyncStatus, queryRequest.getSyncStatus());
        }
        if (queryRequest.getStatus() != null) {
            queryWrapper.eq(KnowledgeFeatureDocumentDO::getStatus, queryRequest.getStatus());
        }
        queryWrapper.orderByDesc(KnowledgeFeatureDocumentDO::getUpdateTime);

        long current = queryRequest.getCurrent() == null || queryRequest.getCurrent() <= 0
                ? 1L
                : queryRequest.getCurrent();
        long size = queryRequest.getSize() == null || queryRequest.getSize() <= 0
                ? 10L
                : queryRequest.getSize();
        Page<KnowledgeFeatureDocumentDO> pageResult = knowledgeFeatureDocumentService.page(
                new Page<>(current, size), queryWrapper);
        Page<KnowledgeFeatureDocumentVO> voPage = new Page<>(
                pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(BeanUtil.copyToList(pageResult.getRecords(), KnowledgeFeatureDocumentVO.class));
        return voPage;
    }

    @Override
    public Boolean saveKnowledgeFeatureDocument(KnowledgeFeatureDocumentReq req) {
        if (req == null) {
            throw new ServiceException("知识库特征文档请求不能为空");
        }
        if (req.getRiskDetailsId() == null) {
            throw new ServiceException("风险小类ID不能为空");
        }
        if (!StringUtils.hasText(req.getFeatureText())) {
            throw new ServiceException("特征文本不能为空");
        }

        RiskDetailsDO riskDetailsDO = riskDetailsService.getById(req.getRiskDetailsId());
        if (riskDetailsDO == null) {
            throw new ServiceException("风险小类不存在");
        }

        String normalizedText = normalizeFeatureText(req.getFeatureText());
        if (!StringUtils.hasText(normalizedText)) {
            throw new ServiceException("特征文本归一化后不能为空");
        }
        String contentHash = sha256(normalizedText);
        if (existsByContentHash(contentHash)) {
            log.info("知识库特征文档已存在，跳过重复保存，contentHash: {}", contentHash);
            return true;
        }

        KnowledgeFeatureDocumentDO knowledgeFeatureDocumentDO = BeanUtil.copyProperties(req, KnowledgeFeatureDocumentDO.class);
        knowledgeFeatureDocumentDO.setId(IdUtil.getSnowflakeNextId());
        knowledgeFeatureDocumentDO.setDocumentId(IdUtil.getSnowflakeNextId());
        knowledgeFeatureDocumentDO.setRiskDetailsName(riskDetailsDO.getDetailsName());
        knowledgeFeatureDocumentDO.setCategoryId(riskDetailsDO.getCategoryId());
        knowledgeFeatureDocumentDO.setNormalizedText(normalizedText);
        knowledgeFeatureDocumentDO.setContentHash(contentHash);
        knowledgeFeatureDocumentDO.setSyncStatus(KbSyncStatusEnums.PENDING.getCode());
        if (knowledgeFeatureDocumentDO.getThreshold() == null) {
            knowledgeFeatureDocumentDO.setThreshold(BigDecimal.ONE);
        }
        if (knowledgeFeatureDocumentDO.getStatus() == null) {
            knowledgeFeatureDocumentDO.setStatus(1);
        }
        LocalDateTime now = LocalDateTime.now();
        knowledgeFeatureDocumentDO.setCreateTime(now);
        knowledgeFeatureDocumentDO.setUpdateTime(now);
        try {
            return knowledgeFeatureDocumentService.save(knowledgeFeatureDocumentDO);
        } catch (DuplicateKeyException ex) {
            if (existsByContentHash(contentHash)) {
                log.info("知识库特征文档已被并发请求保存，按幂等成功处理，contentHash: {}", contentHash);
                return true;
            }
            throw ex;
        }
    }

    private boolean existsByContentHash(String contentHash) {
        return knowledgeFeatureDocumentService.count(
                new LambdaQueryWrapper<KnowledgeFeatureDocumentDO>()
                        .eq(KnowledgeFeatureDocumentDO::getContentHash, contentHash)) > 0;
    }

    private String normalizeFeatureText(String featureText) {
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

    private String sha256(String value) {
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

    @Override
    public Boolean publishVersion(PublishVersionReq req) {
        Object object = redisTemplate.opsForValue().get("knowledge_feature_publish_version:" + req.getRiskDetailsId());
        if (ObjectUtil.isNotNull(object)) {
            throw new ServiceException("当前存在正在发布的版本,请稍候再试");
        }
        //1. 先判断当前风险分类下是否有存在未发布样本
        long count = knowledgeFeatureDocumentService.count(
                new LambdaQueryWrapper<KnowledgeFeatureDocumentDO>()
                        .eq(KnowledgeFeatureDocumentDO::getRiskDetailsId, req.getRiskDetailsId())
                        .isNull(KnowledgeFeatureDocumentDO::getCurrentVersion)
                        .isNull(KnowledgeFeatureDocumentDO::getCurrentVersionId));
        if (count == 0) {
            throw new ServiceException("当前风险分类下不存在未发布的样本,无需发布");
        }
        redisTemplate.opsForValue().set("knowledge_feature_publish_version:" + req.getRiskDetailsId(), "INIT");

        //2. 查询是否存在当前版本
        KnowledgeFeatureDocumentVersionSnapshotDO versionSnapshotDO = knowledgeFeatureDocumentVersionSnapshotService.getOne(
                new LambdaQueryWrapper<KnowledgeFeatureDocumentVersionSnapshotDO>()
                        .eq(KnowledgeFeatureDocumentVersionSnapshotDO::getRiskDetailsId, req.getRiskDetailsId())
                        .orderByDesc(KnowledgeFeatureDocumentVersionSnapshotDO::getUpdateTime)
                        .last("limit 1"));

        try {
            if (versionSnapshotDO == null) {
                versionSnapshotDO = new KnowledgeFeatureDocumentVersionSnapshotDO();
                // 当前版本不存在，直接初始化一个全新版本
                Long snowflakeNextId = IdUtil.getSnowflakeNextId();
                versionSnapshotDO.setVersionId(snowflakeNextId);
                versionSnapshotDO.setBeforeVersionId(snowflakeNextId);
                versionSnapshotDO.setRiskDetailsId(req.getRiskDetailsId());
                versionSnapshotDO.setVersion(1);
                versionSnapshotDO.setStatus("INIT");
                versionSnapshotDO.setCreator("admin");
                versionSnapshotDO.setUpdater("admin");
                versionSnapshotDO.setCreateTime(LocalDateTime.now());
                versionSnapshotDO.setUpdateTime(LocalDateTime.now());
                knowledgeFeatureDocumentVersionSnapshotService.save(versionSnapshotDO);
            } else {
                Long versionId = versionSnapshotDO.getVersionId();
                versionSnapshotDO.setVersionId(IdUtil.getSnowflakeNextId());
                versionSnapshotDO.setBeforeVersionId(versionId);
                versionSnapshotDO.setVersion(versionSnapshotDO.getVersion() + 1);
                versionSnapshotDO.setCreateTime(LocalDateTime.now());
                versionSnapshotDO.setUpdateTime(LocalDateTime.now());
                knowledgeFeatureDocumentVersionSnapshotService.save(versionSnapshotDO);
            }

        } catch (Exception e) {
            log.error("发布知识特征版本失败", e);
            redisTemplate.delete("knowledge_feature_publish_version:" + req.getRiskDetailsId());
            throw new ServiceException("发布知识特征版本失败");
        }
        // 发送消息
        messageQueueProducer.send(MessageSendRequest.builder()
                .topic(KNOWLEDGE_PUBLISH_VERSION_TOPIC)
                .keys(versionSnapshotDO.getVersionId().toString())
                .body(DocumentPublishVersionMessage
                        .builder()
                        .riskDetailsId(req.getRiskDetailsId())
                        .versionId(versionSnapshotDO.getVersionId())
                        .beforeVersionId(versionSnapshotDO.getBeforeVersionId())
                        .build())
                .bizDesc("发布知识特征版本")
                .build());
        return true;
    }
}
