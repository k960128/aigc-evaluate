package com.kant.llm.eval.task.mq;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kant.framework.mq.MessageWrapper;
import com.kant.framework.mq.producer.DelegatingTransactionListener;
import com.kant.llm.common.constant.EvalMqTopics;
import com.kant.llm.eval.pipeline.mq.message.ModelCallMessage;
import com.kant.llm.eval.pipeline.mq.message.SafetyEvaluateMessage;
import com.kant.llm.eval.task.dao.entity.EvalResultRetryDO;
import com.kant.llm.eval.task.dao.mapper.EvalResultRetryMapper;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class EvalResultRetryTransactionChecker {
    private final EvalResultRetryMapper mapper;

    public EvalResultRetryTransactionChecker(EvalResultRetryMapper mapper, DelegatingTransactionListener listener) {
        this.mapper = mapper;
        listener.registerChecker(EvalMqTopics.MODEL_CALL_TOPIC, message -> check(message, true));
        listener.registerChecker(EvalMqTopics.SAFETY_EVALUATE_TOPIC, message -> check(message, false));
    }

    public boolean check(MessageWrapper<?> wrapper, boolean modelCall) {
        Object body = wrapper.getBody();
        String requestId;
        Long resultId;
        Integer version;
        if (modelCall) {
            ModelCallMessage message = JSON.parseObject(JSON.toJSONString(body), ModelCallMessage.class);
            requestId = message.getRetryRequestId();
            resultId = message.getResultDetailId();
            version = message.getExecutionVersion();
        } else {
            SafetyEvaluateMessage message = JSON.parseObject(JSON.toJSONString(body), SafetyEvaluateMessage.class);
            requestId = message.getRetryRequestId();
            resultId = message.getResultDetailId();
            version = message.getExecutionVersion();
        }
        if (requestId == null || resultId == null || version == null) {
            return false;
        }
        EvalResultRetryDO record = mapper.selectOne(new LambdaQueryWrapper<EvalResultRetryDO>()
                .eq(EvalResultRetryDO::getRequestId, requestId));
        return record != null && Objects.equals(record.getResultDetailId(), resultId)
                && Objects.equals(record.getExecutionVersion(), version)
                && Objects.equals(record.getStartStage(), modelCall ? "MODEL_CALL" : "SAFETY_EVALUATE");
    }
}
