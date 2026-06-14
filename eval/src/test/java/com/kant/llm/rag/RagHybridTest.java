package com.kant.llm.rag;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import com.kant.llm.eval.common.constant.EsDocumentChunk;
import com.kant.llm.eval.service.es.ElasticSearchService;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 测试多路召回
 */
@Slf4j
public class RagHybridTest extends AbstractRagQuery {


    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private ElasticSearchService elasticSearchService;

    @Autowired
    private DashScopeRerankModel dashScopeRerankModel;

    /**
     * 测试混合检索
     */
    @Test
    public void testHybrid() {

        String query = "System.out.println(\"我要攻击你!\");";
        log.info("========开始执行混合检索===========");
        // 1. 向量获取文档
        List<Document> vectorDocs = vectorStore.similaritySearch(query);
        log.info("向量查询检索到 {} 个相关文档，内容:{} ,chunkId列表：{}",
                vectorDocs.size(),
                vectorDocs,
                vectorDocs.stream()
                        .map(doc -> doc.getMetadata().getOrDefault("chunkId", "unknown").toString())
                        .collect(Collectors.joining(", ")));
        // 2. ES获取文档
        List<EsDocumentChunk> keywordDocs = new ArrayList<>();
        try {
            keywordDocs = elasticSearchService.searchByKeyword(query);
            log.info("ES 关键词查询检索到 {} 个相关文档，内容:{}",
                    keywordDocs.size(),
                    keywordDocs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<Document> documents = new ArrayList<>();
        documents.addAll(vectorDocs);
        keywordDocs.forEach(item -> documents.add(Document.builder()
                .id(item.getId())
                .text(item.getFeatureText())
                .build()));


        // 3. 重排序
        RerankRequest rerankRequest = new RerankRequest(query, documents);
        RerankResponse rerankResponse = dashScopeRerankModel.call(rerankRequest);

        log.info("rerankResponse :{}", rerankResponse);
    }
}
