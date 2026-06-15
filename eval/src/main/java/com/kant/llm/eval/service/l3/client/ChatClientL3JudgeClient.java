package com.kant.llm.eval.service.l3.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kant.llm.eval.common.condition.ConditionalOnL3JudgeMode;
import com.kant.llm.eval.common.enums.L3DecisionTypeEnums;
import com.kant.llm.eval.service.l3.model.L3JudgeRequest;
import com.kant.llm.eval.service.l3.model.L3JudgeResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于裁判大模型的 L3 Judge 客户端。
 *
 * <p>该实现只负责调用 JudgeConfiguration 暴露的 judgeChatClient，并把模型返回内容解析为
 * L3JudgeResult。任何调用异常、非 JSON、字段非法或决策非法都会降级为 DEGRADED_MANUAL_REVIEW，
 * 避免 L3 真实模型波动影响批次推进。</p>
 */
@Slf4j
@Component
@ConditionalOnL3JudgeMode("real")
public class ChatClientL3JudgeClient implements L3JudgeClient {

    private static final BigDecimal MIN_CONFIDENCE = BigDecimal.ZERO;

    private static final BigDecimal MAX_CONFIDENCE = BigDecimal.ONE;

    private final ChatClient judgeChatClient;

    private final ObjectMapper objectMapper;

    public ChatClientL3JudgeClient(@Qualifier("judgeChatClient") ChatClient judgeChatClient,
                                   ObjectMapper objectMapper) {
        this.judgeChatClient = judgeChatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public L3JudgeResult judge(L3JudgeRequest request) {
        Long targetRiskDetailsId = request == null ? null : request.getTargetRiskDetailsId();
        String prompt = request == null ? null : request.getJudgePrompt();
        if (StringUtils.isBlank(prompt)) {
            return degraded(targetRiskDetailsId, null, "L3 Judge Prompt 为空，进入降级人工核验。");
        }

        try {
            log.info("L3 真实 Judge 开始调用，targetRiskDetailsId: {}", targetRiskDetailsId);
            String rawResponse = judgeChatClient.prompt(prompt).call().content();
            L3JudgeResult result = parseJudgeResponse(targetRiskDetailsId, rawResponse);
            log.info("L3 真实 Judge 返回完成，targetRiskDetailsId: {}, decision: {}, confidence: {}",
                    targetRiskDetailsId, result.getDecision().getCode(), result.getConfidence());
            return result;
        } catch (Exception ex) {
            log.warn("L3 真实 Judge 调用或解析异常，进入降级人工核验，targetRiskDetailsId: {}, reason: {}",
                    targetRiskDetailsId, ex.getMessage(), ex);
            return degraded(targetRiskDetailsId, null, "L3 真实 Judge 调用或解析异常，进入人工核验：" + ex.getMessage());
        }
    }

    /**
     * 解析大模型返回的严格 JSON。
     *
     * <p>模型偶尔会包裹 Markdown 代码块或附带少量解释文本，这里只截取最外层 JSON 对象。
     * 如果依然无法解析，则返回降级人工核验。</p>
     */
    private L3JudgeResult parseJudgeResponse(Long targetRiskDetailsId, String rawResponse) {
        if (StringUtils.isBlank(rawResponse)) {
            return degraded(targetRiskDetailsId, rawResponse, "L3 真实 Judge 返回为空，进入人工核验。");
        }

        String jsonText = extractJsonObject(rawResponse);
        try {
            JsonNode root = objectMapper.readTree(jsonText);
            L3DecisionTypeEnums decision = parseDecision(root.path("decision").asText(null));
            if (decision == null || decision == L3DecisionTypeEnums.DEGRADED_MANUAL_REVIEW) {
                return degraded(targetRiskDetailsId, rawResponse, "L3 真实 Judge 返回的 decision 非法，进入人工核验。");
            }

            BigDecimal confidence = normalizeConfidence(root.get("confidence"));
            Long riskDetailsId = parseRiskDetailsId(root.get("riskDetailsId"), targetRiskDetailsId);
            String reason = StringUtils.defaultIfBlank(root.path("reason").asText(null), decision.getDesc());
            List<String> evidence = parseEvidence(root.get("evidence"));

            return L3JudgeResult.builder()
                    .decision(decision)
                    .confidence(confidence)
                    .riskDetailsId(riskDetailsId)
                    .reason(reason)
                    .evidence(evidence)
                    .rawResponse(rawResponse)
                    .degraded(false)
                    .build();
        } catch (Exception ex) {
            log.warn("L3 真实 Judge JSON 解析失败，targetRiskDetailsId: {}, jsonText: {}, reason: {}",
                    targetRiskDetailsId, jsonText, ex.getMessage());
            return degraded(targetRiskDetailsId, rawResponse, "L3 真实 Judge 返回不是合法 JSON，进入人工核验。");
        }
    }

    private String extractJsonObject(String rawResponse) {
        String text = rawResponse.trim();
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return text;
    }

    private L3DecisionTypeEnums parseDecision(String decisionText) {
        if (StringUtils.isBlank(decisionText)) {
            return null;
        }
        for (L3DecisionTypeEnums decision : L3DecisionTypeEnums.values()) {
            if (decision.getCode().equalsIgnoreCase(decisionText.trim())) {
                return decision;
            }
        }
        return null;
    }

    private BigDecimal normalizeConfidence(JsonNode confidenceNode) {
        BigDecimal confidence = parseBigDecimal(confidenceNode);
        if (confidence == null) {
            return MIN_CONFIDENCE;
        }
        if (confidence.compareTo(MIN_CONFIDENCE) < 0) {
            return MIN_CONFIDENCE;
        }
        if (confidence.compareTo(MAX_CONFIDENCE) > 0) {
            return MAX_CONFIDENCE;
        }
        return confidence;
    }

    private BigDecimal parseBigDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (Exception ex) {
            return null;
        }
    }

    private Long parseRiskDetailsId(JsonNode riskDetailsIdNode, Long targetRiskDetailsId) {
        if (riskDetailsIdNode == null || riskDetailsIdNode.isNull()) {
            return targetRiskDetailsId;
        }
        try {
            if (riskDetailsIdNode.isNumber()) {
                return riskDetailsIdNode.longValue();
            }
            String riskDetailsIdText = riskDetailsIdNode.asText(null);
            if (StringUtils.isBlank(riskDetailsIdText)) {
                return targetRiskDetailsId;
            }
            return Long.valueOf(riskDetailsIdText.trim());
        } catch (Exception ex) {
            return targetRiskDetailsId;
        }
    }

    private List<String> parseEvidence(JsonNode evidenceNode) {
        List<String> evidence = new ArrayList<>();
        if (evidenceNode == null || evidenceNode.isNull()) {
            return evidence;
        }
        if (evidenceNode.isArray()) {
            for (JsonNode item : evidenceNode) {
                String value = item.asText(null);
                if (StringUtils.isNotBlank(value)) {
                    evidence.add(value);
                }
            }
            return evidence;
        }
        String singleEvidence = evidenceNode.asText(null);
        if (StringUtils.isNotBlank(singleEvidence)) {
            evidence.add(singleEvidence);
        }
        return evidence;
    }

    private L3JudgeResult degraded(Long targetRiskDetailsId, String rawResponse, String reason) {
        return L3JudgeResult.builder()
                .decision(L3DecisionTypeEnums.DEGRADED_MANUAL_REVIEW)
                .confidence(BigDecimal.ZERO)
                .riskDetailsId(targetRiskDetailsId)
                .reason(reason)
                .evidence(List.of())
                .rawResponse(rawResponse)
                .degraded(true)
                .build();
    }
}
