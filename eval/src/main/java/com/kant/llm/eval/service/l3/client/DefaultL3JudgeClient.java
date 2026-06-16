package com.kant.llm.eval.service.l3.client;

import com.kant.llm.eval.common.condition.ConditionalOnL3JudgeMode;
import com.kant.llm.eval.common.enums.L3DecisionTypeEnums;
import com.kant.llm.eval.service.l3.model.L3JudgeRequest;
import com.kant.llm.eval.service.l3.model.L3JudgeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * L3 Judge 默认降级客户端。
 *
 * <p>当 app.l3.judge-mode=default 时生效，不调用真实裁判大模型，
 * 只把 L2 模糊区稳定转入人工核验，便于本地排障或模型不可用时兜底。</p>
 */
@Slf4j
@Component
@ConditionalOnL3JudgeMode("default")
public class DefaultL3JudgeClient implements L3JudgeClient {

    @Override
    public L3JudgeResult judge(L3JudgeRequest request) {
        Long targetRiskDetailsId = request == null ? null : request.getTargetRiskDetailsId();
        log.info("L3 默认降级 Judge 生效，targetRiskDetailsId: {}", targetRiskDetailsId);
        return L3JudgeResult.builder()
                .decision(L3DecisionTypeEnums.DEGRADED_MANUAL_REVIEW)
                .confidence(BigDecimal.ZERO)
                .riskDetailsId(targetRiskDetailsId)
                .reason("真实 L3 Judge 尚未接入，L2 模糊区样本进入人工核验。")
                .evidence(List.of())
                .rawResponse(null)
                .degraded(true)
                .build();
    }
}
