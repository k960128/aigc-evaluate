package com.kant.llm.eval.pipeline.mq.producer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.framework.mq.MessageWrapper;
import com.kant.framework.mq.producer.DelegatingTransactionListener;
import com.kant.framework.mq.producer.TransactionChecker;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.common.enums.AcSnapshotPublishStatusEnums;
import com.kant.llm.eval.knowledge.dao.entity.AcSnapshotPublishRecordDO;
import com.kant.llm.eval.pipeline.mq.message.RiskVocabularyAcPublishMessage;
import com.kant.llm.eval.knowledge.service.AcSnapshotPublishRecordService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AC 快照构建事务消息回查器。
 *
 * <p>这里只校验生产端本地事务是否已提交；消费者构建结果由节点 ACK 表和协调器处理。</p>
 */
@Component
@RequiredArgsConstructor
public class RiskVocabularyAcTransactionChecker implements TransactionChecker {

    private final DelegatingTransactionListener delegatingTransactionListener;

    private final AcSnapshotPublishRecordService publishRecordService;

    private final StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void register() {
        delegatingTransactionListener.registerChecker(EvalMqTopics.RISK_VOCABULARY_AC_PUBLISH_TOPIC, this);
    }

    @Override
    public boolean check(MessageWrapper<?> message) {
        if (message == null || !(message.getBody() instanceof RiskVocabularyAcPublishMessage body)) {
            return false;
        }
        AcSnapshotPublishRecordDO record = publishRecordService.getOne(
                new LambdaQueryWrapper<AcSnapshotPublishRecordDO>()
                        .eq(AcSnapshotPublishRecordDO::getVersionId, body.getVersionId())
                        .last("LIMIT 1"));
        if (record == null || AcSnapshotPublishStatusEnums.FAILED.getCode().equals(record.getStatus())) {
            return false;
        }
        String manifestJson = stringRedisTemplate.opsForValue().get(body.getManifestKey());
        return StringUtils.hasText(manifestJson);
    }
}
