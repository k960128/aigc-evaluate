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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
    private final TransactionTemplate transactionTemplate;
    // ======================================

    @Override
    public KnowledgeFeatureDocumentVersionVO getKnowledgeFeatureDocumentVersion() {
        KnowledgeFeatureDocumentVersionDO knowledgeFeatureDocumentVersionDO =
                knowledgeFeatureDocumentVersionService.getOne(null);
        if (knowledgeFeatureDocumentVersionDO == null) {
            return null;
        }
        return BeanUtil.copyProperties(knowledgeFeatureDocumentVersionDO, KnowledgeFeatureDocumentVersionVO.class);
    }

    @Override
    public List<KnowledgeFeatureDocumentVersionSnapshotVO> getKnowledgeFeatureDocumentVersionSnapshotList() {
        List<KnowledgeFeatureDocumentVersionSnapshotDO> list = knowledgeFeatureDocumentVersionSnapshotService.list();
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
        knowledgeFeatureDocumentDO.setDocumentId(UUID.randomUUID().toString());
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
    public Boolean publishVersion() {
        Object object = redisTemplate.opsForValue().get("knowledge_feature_publish_version:lock");
        if (ObjectUtil.isNotNull(object)) {
            throw new ServiceException("当前存在正在发布的版本,请稍候再试");
        }
        //1. 判断样本库中是否存在未发布的样本
        long count = knowledgeFeatureDocumentService.count(
                new LambdaQueryWrapper<KnowledgeFeatureDocumentDO>()
                        .isNull(KnowledgeFeatureDocumentDO::getCurrentVersion)
                        .isNull(KnowledgeFeatureDocumentDO::getCurrentVersionId));
        if (count == 0) {
            throw new ServiceException("样本库中不存在未发布的样本,无需发布");
        }

        // redis记录发布状态
        redisTemplate.opsForValue().set("knowledge_feature_publish_version:lock", "1");
        Long versionId = null;
        Long beforeVersionId = null;
        // 2. 判断是否存在发布的版本
        KnowledgeFeatureDocumentVersionDO documentVersionDO = knowledgeFeatureDocumentVersionService.getOne(null);
        // 当前为第一次发布，初始化数据库信息
        if (ObjectUtil.isNull(documentVersionDO)) {
            // 创建版本流水记录
            long snowflakeNextId = IdUtil.getSnowflakeNextId();
            versionId = snowflakeNextId;
            beforeVersionId = snowflakeNextId;
            KnowledgeFeatureDocumentVersionSnapshotDO versionSnapshotDO = KnowledgeFeatureDocumentVersionSnapshotDO.builder()
                    .versionId(snowflakeNextId)
                    .version(1)
                    .beforeVersionId(snowflakeNextId)
                    .status("INIT")
                    .creator("admin")
                    .updater("admin")
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            // 创建版本记录
            documentVersionDO = KnowledgeFeatureDocumentVersionDO.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .status("INIT")
                    .currentVersionId(versionSnapshotDO.getVersionId())
                    .lockVersion(0)
                    .creator("admin")
                    .updater("admin")
                    .deleted(false)
                    .build();

            KnowledgeFeatureDocumentVersionDO finalDocumentVersionDO = documentVersionDO;

            transactionTemplate.executeWithoutResult(status -> {
                try {
                    knowledgeFeatureDocumentVersionSnapshotService.save(versionSnapshotDO);
                    knowledgeFeatureDocumentVersionService.save(finalDocumentVersionDO);
                } catch (Exception e) {
                    log.error("提交发布失败", e);
                    redisTemplate.delete("knowledge_feature_publish_version:lock");
                    throw new ServiceException("提交发布失败，请稍候重试，或者联系管理员！");
                }
            });
        } else {
            KnowledgeFeatureDocumentVersionSnapshotDO snapshotVersionDO = knowledgeFeatureDocumentVersionSnapshotService.getById(documentVersionDO.getCurrentVersionId());

            long currentVersionId = IdUtil.getSnowflakeNextId();
            versionId = currentVersionId;
            beforeVersionId = snapshotVersionDO.getVersionId();

            KnowledgeFeatureDocumentVersionSnapshotDO versionSnapshotDO = KnowledgeFeatureDocumentVersionSnapshotDO.builder()
                    .versionId(currentVersionId)
                    .beforeVersionId(snapshotVersionDO.getVersionId())
                    .version(snapshotVersionDO.getVersion() + 1)
                    .status("INIT")
                    .creator("admin")
                    .updater("admin")
                    .build();
            documentVersionDO.setCurrentVersionId(currentVersionId);
            documentVersionDO.setStatus("INIT");

            KnowledgeFeatureDocumentVersionDO finalDocumentVersionDO1 = documentVersionDO;
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    knowledgeFeatureDocumentVersionSnapshotService.save(versionSnapshotDO);
                    knowledgeFeatureDocumentVersionService.updateById(finalDocumentVersionDO1);
                } catch (Exception e) {
                    log.error("提交发布失败", e);
                    redisTemplate.delete("knowledge_feature_publish_version:lock");
                    throw new ServiceException("提交发布失败，请稍候重试，或者联系管理员！");
                }
            });
        }


        // 发送消息
        messageQueueProducer.send(MessageSendRequest.builder()
                .topic(KNOWLEDGE_PUBLISH_VERSION_TOPIC)
                .keys(documentVersionDO.getCurrentVersionId().toString())
                .body(DocumentPublishVersionMessage
                        .builder()
                        .versionId(versionId)
                        .beforeVersionId(beforeVersionId)
                        .build())
                .bizDesc("发布知识特征版本")
                .build());
        return true;
    }
}
