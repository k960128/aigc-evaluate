package com.kant.llm.eval.knowledge.mq.v2.consumer;

import com.kant.framework.mq.MessageWrapper;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.eval.knowledge.mq.v2.message.DocumentPublishVersionMessage;
import com.kant.llm.eval.knowledge.service.KnowledgeFeatureDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = EvalMqTopics.KNOWLEDGE_PUBLISH_VERSION_TOPIC,
        consumerGroup = "document-publish-version-message-consumer-group"
)
public class DocumentPublishVersionConsumer
        implements RocketMQListener<MessageWrapper<DocumentPublishVersionMessage>> {

    private final RedisTemplate<String,Object> redisTemplate;
    private final KnowledgeFeatureDocumentService knowledgeFeatureDocumentService;

    public DocumentPublishVersionConsumer(RedisTemplate<String, Object> redisTemplate,
                                          KnowledgeFeatureDocumentService knowledgeFeatureDocumentService) {
        this.redisTemplate = redisTemplate;
        this.knowledgeFeatureDocumentService = knowledgeFeatureDocumentService;
    }

    @Override
    public void onMessage(MessageWrapper<DocumentPublishVersionMessage> messageWrapper) {
        DocumentPublishVersionMessage message = messageWrapper.getBody();
        log.info("[消费者] bizDesc: {}，topic: {}, riskDetailsId: {}, versionId: {}", messageWrapper.getBizDesc(),
                EvalMqTopics.KNOWLEDGE_PUBLISH_VERSION_TOPIC, message.getRiskDetailsId(), message.getVersionId());
        // 执行发布操作
        execute(message);
        // 删除发布状态缓存
        redisTemplate.delete("knowledge_feature_publish_version:" + message.getRiskDetailsId());
    }

    /**
     * 执行发布操作
     */
    private void execute(DocumentPublishVersionMessage message) {
        Long riskDetailsId = message.getRiskDetailsId();
        Long beforeVersionId = message.getBeforeVersionId();
        //1. 获取待发布和上一个版本的所有文档内容
        knowledgeFeatureDocumentService.listByRiskDetailsIdAndVersionId(riskDetailsId,beforeVersionId);
        //2. 发布ES
        //3. 发布向量数据库
        //4. 更新发布状态，并且将指针指向最新版本
    }
}
