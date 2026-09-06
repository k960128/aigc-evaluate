package com.kant.llm.eval.newpipeline.engine;

import com.kant.llm.common.constant.EsDocumentChunkV2;
import com.kant.llm.eval.pipeline.service.es.ElasticSearchService;
import com.kant.llm.eval.pipeline.service.l2.model.L2EvaluationRequest;
import com.kant.llm.eval.pipeline.service.l2.model.L2EvaluationResult;
import com.kant.llm.eval.risk.service.RiskDetailRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * L2 安全评估执行引擎
 *
 * @author kant
 */
@Slf4j
@Component
public class L2SafetyEvaluateExecutorEngine {
    private static final int DEFAULT_TOP_K = 5;

    private final VectorStore vectorStore;
    private final ElasticSearchService elasticSearchService;
    private final RiskDetailRuleService riskDetailRuleService;

    public L2SafetyEvaluateExecutorEngine(VectorStore vectorStore,
                                          ElasticSearchService elasticSearchService,
                                          RiskDetailRuleService riskDetailRuleService) {
        this.vectorStore = vectorStore;
        this.elasticSearchService = elasticSearchService;
        this.riskDetailRuleService = riskDetailRuleService;
    }

    /**
     * 执行L2评测
     *
     * @return resp
     */
    public L2EvaluationResult execute(L2EvaluationRequest request) {
        log.info("[安全评测L2]，请求参数： {}", request);
        String queryText = request.getInputText();
        // 1. ES召回
        List<EsDocumentChunkV2> esRecallResult = null;
        try {
            esRecallResult = elasticSearchService.searchByKeywordV2(queryText, request.getTargetRiskDetailsId());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 2. 向量召回
        List<Document> vectorRecallResult = vectorStore.similaritySearch(SearchRequest
                .builder()
                .query(queryText)
                .topK(5)
                .similarityThreshold(0.85) // 获取分数大于0.85的文档
                .filterExpression("riskDetailsId == " + request.getTargetRiskDetailsId())
                .build());
        // 3. RRF融合
        List<Document> rrfFusion = rrfFusion(vectorRecallResult, esRecallResult, DEFAULT_TOP_K);
        // 4. ReRank
        // 5. 阈值路由判定
        return L2EvaluationResult.builder().build();
    }

    /**
     * RRF融合
     * @param vectorDocuments 语义相似度召回
     * @param esDocuments 关键词召回
     * @param topK 5
     * @return resp
     */
    public List<Document> rrfFusion(List<Document> vectorDocuments, List<EsDocumentChunkV2> esDocuments, int topK) {
        // 常数 k，控制低排名文档的权重
        final int K = 60;
        // 存储每个文档ID的RRF得分
        Map<String, Double> rrfScores = new HashMap<>();

        // 处理向量检索结果（排名从1开始）
        for (int i = 0; i < vectorDocuments.size(); i++) {
            Document doc = vectorDocuments.get(i);
            String docId = doc.getId();
            // 排名从1开始
            int rank = i + 1;
            double score = 1.0 / (K + rank);
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0.0) + score);
        }

        // 处理关键词检索结果（排名从1开始）
        for (int i = 0; i < esDocuments.size(); i++) {
            EsDocumentChunkV2 doc = esDocuments.get(i);
            String docId = doc.getId();
            // 排名从1开始
            int rank = i + 1;
            double score = 1.0 / (K + rank);
            rrfScores.put(docId, rrfScores.getOrDefault(docId, 0.0) + score);
        }

        // 收集所有文档ID并按RRF得分降序排序，同时限制返回topK条
        List<String> sortedDocIds = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(topK)
                .toList();

        // 打印每个文本块的chunkId和分数
        String scoresLog = sortedDocIds.stream()
                .map(docId -> {
                    double score = rrfScores.getOrDefault(docId, 0.0);
                    return String.format("docId: %s, RRF Score: %.4f", docId, score);
                })
                .collect(Collectors.joining("; "));

        log.info("RRF融合后top{}结果：{}", topK, scoresLog);

        // 构建文档ID到内容的映射
        Map<String, String> idToContent = new HashMap<>();
        vectorDocuments.forEach(doc -> idToContent.putIfAbsent(doc.getId(), doc.getText()));
        esDocuments.forEach(doc -> idToContent.putIfAbsent(doc.getId(), doc.getFeatureText()));

        // 按排序后的ID提取文档内容
        return sortedDocIds.stream()
                .map(idToContent::get)
                .filter(Objects::nonNull)
                .map(Document::new)
                .collect(Collectors.toList());
    }
}
