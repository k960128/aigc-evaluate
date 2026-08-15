package com.kant.ai.infra.rerank.strategy;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import com.kant.ai.infra.rerank.RerankModelInfo;
import com.kant.ai.infra.rerank.RerankModelStrategy;
import com.kant.ai.infra.rerank.RerankServiceResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Objects;

/**
 * DashScope/Qwen Rerank 模型策略。
 *
 * <p>当前默认使用 qwen3-rerank。厂商 SDK 初始化、参数兜底和真实调用都收敛在这里，
 * 业务侧不再直接 new DashScopeRerankModel。</p>
 */
@Slf4j
public class DashScopeRerankModelStrategy implements RerankModelStrategy {

    private static final String DEFAULT_MODEL = "qwen3-rerank";

    private static final int DEFAULT_TOP_N = 5;

    @Override
    public Object buildRerankModel(RerankModelInfo modelInfo) {
        return new DashScopeRerankModel(DashScopeApi
                .builder()
                .apiKey(modelInfo.getApiKey())
                .build(),
                DashScopeRerankOptions.builder()
                        .model(resolveModel(modelInfo))
                        .topN(resolveTopN(modelInfo))
                        .build());
    }

    @Override
    public RerankServiceResult rerank(RerankModelInfo modelInfo,
                                      Object rerankModel,
                                      String queryText,
                                      List<String> documents) {
        long startTime = System.currentTimeMillis();
        try {
            DashScopeRerankModel dashScopeRerankModel = (DashScopeRerankModel) rerankModel;
            List<Document> springAiDocuments = toSpringAiDocuments(documents);
            if (springAiDocuments.isEmpty()) {
                return RerankServiceResult.builder()
                        .degraded(true)
                        .reason("Rerank documents 转换后为空")
                        .elapsed(System.currentTimeMillis() - startTime)
                        .manufacturer(getManufacturer())
                        .model(resolveModel(modelInfo))
                        .modelCode(modelInfo.getModelCode())
                        .build();
            }
            log.info("DashScope Rerank 开始调用，modelId: {}, modelCode: {}, model: {}, documentSize: {}",
                    modelInfo.getModelId(), modelInfo.getModelCode(), resolveModel(modelInfo), springAiDocuments.size());
            RerankResponse response = dashScopeRerankModel.call(new RerankRequest(queryText, springAiDocuments));
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("DashScope Rerank 调用完成，modelId: {}, modelCode: {}, elapsed: {}ms",
                    modelInfo.getModelId(), modelInfo.getModelCode(), elapsed);
            return RerankServiceResult.builder()
                    .rawResponse(response)
                    .degraded(false)
                    .elapsed(elapsed)
                    .manufacturer(getManufacturer())
                    .model(resolveModel(modelInfo))
                    .modelCode(modelInfo.getModelCode())
                    .build();
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.warn("DashScope Rerank 调用失败，进入业务侧降级处理，modelId: {}, modelCode: {}, elapsed: {}ms, reason: {}",
                    modelInfo.getModelId(), modelInfo.getModelCode(), elapsed, ex.getMessage(), ex);
            return RerankServiceResult.builder()
                    .degraded(true)
                    .reason(ex.getMessage())
                    .elapsed(elapsed)
                    .manufacturer(getManufacturer())
                    .model(resolveModel(modelInfo))
                    .modelCode(modelInfo.getModelCode())
                    .build();
        }
    }

    @Override
    public ModelManufacturerEnum getManufacturer() {
        return ModelManufacturerEnum.QWEN;
    }

    private String resolveModel(RerankModelInfo modelInfo) {
        if (modelInfo == null || StringUtils.isBlank(modelInfo.getModel())) {
            return DEFAULT_MODEL;
        }
        return modelInfo.getModel();
    }

    private int resolveTopN(RerankModelInfo modelInfo) {
        if (modelInfo == null || modelInfo.getTopN() == null || modelInfo.getTopN() <= 0) {
            return DEFAULT_TOP_N;
        }
        return modelInfo.getTopN();
    }

    /**
     * 将 infra-ai 对外暴露的纯文本候选转换成 Spring AI Rerank SDK 所需的 Document。
     *
     * <p>这样可以让 RerankService 的公共接口保持轻量，不把 Spring AI 的 Document 类型泄漏给业务调用方。</p>
     */
    private List<Document> toSpringAiDocuments(List<String> documents) {
        return documents.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .filter(Objects::nonNull)
                .map(Document::new)
                .toList();
    }
}
