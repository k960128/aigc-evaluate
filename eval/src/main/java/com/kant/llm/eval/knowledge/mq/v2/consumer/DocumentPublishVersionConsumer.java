package com.kant.llm.eval.knowledge.mq.v2.consumer;

import com.kant.framework.mq.MessageWrapper;
import com.kant.llm.common.constant.EsDocumentChunk;
import com.kant.llm.common.constant.EsKnowledgeFeatureDocumentChunk;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeAttackFeatureDO;
import com.kant.llm.eval.knowledge.dao.entity.KnowledgeFeatureDocumentDO;
import com.kant.llm.eval.knowledge.mq.v2.message.DocumentPublishVersionMessage;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentService;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentVersionService;
import com.kant.llm.eval.pipeline.service.es.ElasticSearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = EvalMqTopics.KNOWLEDGE_PUBLISH_VERSION_TOPIC,
        consumerGroup = "document-publish-version-message-consumer-group"
)
public class DocumentPublishVersionConsumer
        implements RocketMQListener<MessageWrapper<DocumentPublishVersionMessage>> {

    private final VectorStore vectorStore;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ElasticSearchService elasticSearchService;
    private final KnowledgeFeatureDocumentService knowledgeFeatureDocumentService;
    private final KnowledgeFeatureDocumentVersionService knowledgeFeatureDocumentVersionService;

    public DocumentPublishVersionConsumer(VectorStore vectorStore,
                                          ElasticSearchService elasticSearchService,
                                          RedisTemplate<String, Object> redisTemplate,
                                          KnowledgeFeatureDocumentService knowledgeFeatureDocumentService,
                                          KnowledgeFeatureDocumentVersionService knowledgeFeatureDocumentVersionService) {
        this.vectorStore = vectorStore;
        this.redisTemplate = redisTemplate;
        this.elasticSearchService = elasticSearchService;
        this.knowledgeFeatureDocumentService = knowledgeFeatureDocumentService;
        this.knowledgeFeatureDocumentVersionService = knowledgeFeatureDocumentVersionService;
    }

    @Override
    public void onMessage(MessageWrapper<DocumentPublishVersionMessage> messageWrapper) {
        DocumentPublishVersionMessage message = messageWrapper.getBody();
        log.info("[消费者] bizDesc: {}，topic: {}, riskDetailsId: {}, versionId: {}", messageWrapper.getBizDesc(),
                EvalMqTopics.KNOWLEDGE_PUBLISH_VERSION_TOPIC, message.getRiskDetailsId(), message.getVersionId());
        // 执行发布操作
        execute(message);
    }

    /**
     * 执行发布操作
     */
    private void execute(DocumentPublishVersionMessage message) {
        Long riskDetailsId = message.getRiskDetailsId();
        Long beforeVersionId = message.getBeforeVersionId();
        //1. 获取待发布和上一个版本的所有文档内容
        List<KnowledgeFeatureDocumentDO> knowledgeFeatureDocumentDOS = knowledgeFeatureDocumentService.listByRiskDetailsIdAndVersionId(riskDetailsId, beforeVersionId);
        log.info("待发布文档数量: {}", knowledgeFeatureDocumentDOS.size());
        // 原子递增计数器
        AtomicInteger count = new AtomicInteger(0);
        knowledgeFeatureDocumentDOS.forEach(knowledgeFeatureDocumentDO -> {
            try {
                //2. 发布ES
                elasticSearchService.indexSingleVersion2(buildEsDocument(knowledgeFeatureDocumentDO));
                //3. 发布向量数据库
                vectorStore.add(List.of(buildVectorDocument(knowledgeFeatureDocumentDO, String.valueOf(knowledgeFeatureDocumentDO.getDocumentId()))));
                int andIncrement = count.getAndIncrement();
                knowledgeFeatureDocumentDO.setSyncStatus(1);
                knowledgeFeatureDocumentService.updateById(knowledgeFeatureDocumentDO);
                log.info("当前已发布数量:{},总数量:{}", knowledgeFeatureDocumentDOS.size(), andIncrement);
            } catch (Exception e) {
                log.error("发布ES文档失败，documentId: {}", knowledgeFeatureDocumentDO.getDocumentId(), e);
            }
        });
        //4. 更新发布状态，并且将指针指向最新版本
        knowledgeFeatureDocumentVersionService.updateCurrentVersion(riskDetailsId, message.getVersionId());
        // 删除发布状态缓存
        redisTemplate.delete("knowledge_feature_publish_version:" + message.getRiskDetailsId());
    }

    private EsKnowledgeFeatureDocumentChunk buildEsDocument(KnowledgeFeatureDocumentDO knowledgeFeatureDocumentDO) {
        return EsKnowledgeFeatureDocumentChunk.builder()
                .documentId(knowledgeFeatureDocumentDO.getDocumentId())
                .riskDetailsId(knowledgeFeatureDocumentDO.getRiskDetailsId())
                .categoryId(knowledgeFeatureDocumentDO.getCategoryId())
                .contentHash(knowledgeFeatureDocumentDO.getContentHash())
                .createTime(formatDateTime(knowledgeFeatureDocumentDO.getCreateTime()))
                .updateTime(formatDateTime(knowledgeFeatureDocumentDO.getUpdateTime()))
                .currentVersion(knowledgeFeatureDocumentDO.getCurrentVersion())
                .currentVersionId(knowledgeFeatureDocumentDO.getCurrentVersionId())
                .featureText(knowledgeFeatureDocumentDO.getFeatureText())
                .normalizedText(knowledgeFeatureDocumentDO.getNormalizedText())
                .polarity(knowledgeFeatureDocumentDO.getPolarity())
                .source(knowledgeFeatureDocumentDO.getSource())
                .status(knowledgeFeatureDocumentDO.getStatus())
                .threshold(knowledgeFeatureDocumentDO.getThreshold())
                .build();
    }

    private Document buildVectorDocument(KnowledgeFeatureDocumentDO feature, String documentId) {
        return new Document(documentId, feature.getNormalizedText(), buildVectorMetadata(feature));
    }

    private Map<String, Object> buildVectorMetadata(KnowledgeFeatureDocumentDO feature) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfNotNull(metadata, "documentId", feature.getDocumentId());
        putIfNotNull(metadata, "riskDetailsId", feature.getRiskDetailsId());
        putIfNotNull(metadata, "categoryId", feature.getCategoryId());
        putIfNotNull(metadata, "polarity", feature.getPolarity());
        putIfNotNull(metadata, "source", feature.getSource());
        putIfNotNull(metadata, "contentHash", feature.getContentHash());
        putIfNotNull(metadata, "currentVersion", feature.getCurrentVersion());
        putIfNotNull(metadata, "currentVersionId", feature.getCurrentVersionId());
        putIfNotNull(metadata, "status", feature.getStatus());
        putIfNotNull(metadata, "threshold", feature.getThreshold());
        return metadata;
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
