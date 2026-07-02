package com.kant.llm.examples.controller;

import com.alibaba.fastjson2.JSON;
import com.kant.ai.infra.rerank.RerankService;
import com.kant.llm.agent.ai.model.IntentRecognitionResult;
import com.kant.llm.agent.ai.service.IntentRecognitionService;
import com.kant.llm.agent.ai.service.PromptService;
import com.kant.llm.agent.chat.service.ChatMessageService;
import com.kant.llm.eval.pipeline.service.es.ElasticSearchService;
import com.kant.llm.rag.moduler.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/examples/queryTransformer")
@RequiredArgsConstructor
public class ExamplesQueryTransformerController {
    private final ChatMessageService chatMessageService;
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final IntentRecognitionService intentRecognitionService;
    private final PromptService promptService;
    private final ElasticSearchService elasticSearchService;
    private final RerankService rerankService;

    @GetMapping("/testTransformQuery")
    public String transformQuery(@RequestParam("query") String query) {
        String messageId = chatMessageService.saveUserMessage(UUID.randomUUID().toString(), query);
        EvalQueryTransformer evalQueryTransformer = new EvalQueryTransformer(chatClient, messageId);
        Query transform = evalQueryTransformer.transform(new Query(query));
        return transform.text();
    }

    @GetMapping("/testRoute")
    public String testRoute(@RequestParam("query") String query) {

        // 构建文档检索器列表
        List<DocumentRetriever> documentRetrievers = buildRoutingRetrievers();

        // 执行检索路由
        EvalQueryRouter evalQueryRouter = new EvalQueryRouter(documentRetrievers, chatClient);
        List<DocumentRetriever> route = evalQueryRouter.route(new Query(query));
        return JSON.toJSONString(route);
    }

    @GetMapping("testPromptRouter")
    public String testPromptRouter(@RequestParam("query") String query) {
        IntentRecognitionResult intentRecognitionResult = intentRecognitionService.chat(query);
        return promptService.getPrompt(intentRecognitionResult);
    }

    @GetMapping(path = "/testHybridRetriever", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testHybridRetriever(@RequestParam("query") String query) {
        String finalConversationId = UUID.randomUUID().toString();
        // 测试混合检索相关业务

        // 1. 混合检索器
        HybridDocumentRetriever hybridDocumentRetriever = new HybridDocumentRetriever(elasticSearchService, vectorStore);

        // 2. RrfDocumentJoiner
        RrfDocumentJoiner rrfDocumentJoiner = new RrfDocumentJoiner();

        // 3. RerankDocumentPostProcessor
        RerankDocumentPostProcessor rerankDocumentPostProcessor = new RerankDocumentPostProcessor(rerankService);

        QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .build();

        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor
                .builder()
                .documentRetriever(hybridDocumentRetriever)
                .documentJoiner(rrfDocumentJoiner)
                .documentPostProcessors(rerankDocumentPostProcessor)
                .queryAugmenter(queryAugmenter)
                .build();

        return Flux.just("开始处理...")
                .concatWith(chatClient.prompt()
                        .user(query)
                        .advisors(retrievalAugmentationAdvisor)
                        .stream()
                        .content())
                .doOnError(e -> log.error("流式对话异常: conversationId={}", finalConversationId, e))
                .concatWith(Mono.just("[DONE]:" + finalConversationId));
    }

    @GetMapping(path = "/testRetrievalAugmentationAdvisor", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testRetrievalAugmentationAdvisor(@RequestParam("query") String query) {
        String finalConversationId = UUID.randomUUID().toString();
        // 1. 意图识别 + 获取业务 system prompt
        IntentRecognitionResult intentRecognitionResult = intentRecognitionService.chat(query);
        String systemPrompt = promptService.getPrompt(intentRecognitionResult);

        // 2. 构建查询改写器
        EvalQueryTransformer evalQueryTransformer = new EvalQueryTransformer(chatClient, null);

        // 3. 构建检索路由器
        EvalQueryRouter evalQueryRouter = new EvalQueryRouter(buildRoutingRetrievers(), chatClient);

        // 4. 构建文档合并器
        DocumentJoiner documentJoiner = new ConcatenationDocumentJoiner();

        // 5. 构建后处理器：过滤低分文档，最多保留 5 条
        DocumentPostProcessor filterProcessor = (originalQuery, documents) -> documents.stream()
                .filter(document -> document.getScore() == null || document.getScore() >= 0.70)
                .limit(5)
                .toList();

        // 6. 构建上下文注入器
        QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .promptTemplate(new PromptTemplate(systemPrompt))
                .build();

        // 7. 构建 RetrievalAugmentationAdvisor
        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor
                .builder()
                .queryTransformers(evalQueryTransformer)
                .documentRetriever(evalQueryRouter)
                .documentJoiner(documentJoiner)
                .documentPostProcessors(filterProcessor)
                .queryAugmenter(queryAugmenter)
                .build();

        // 8. 发起流式调用
        return Flux.just("开始处理...")
                .concatWith(chatClient.prompt()
                        .user(query)
                        .advisors(retrievalAugmentationAdvisor)
                        .stream()
                        .content())
                .doOnError(e -> log.error("流式对话异常: conversationId={}", finalConversationId, e))
                .concatWith(Mono.just("[DONE]:" + finalConversationId));
    }


    /**
     * 构建多渠道检索器
     * @return list
     */
    private List<DocumentRetriever> buildRoutingRetrievers() {
        // 构建数据库检索器
        EvaSqlDatabaseContentRetriever sqlRetriever = new EvaSqlDatabaseContentRetriever();
        // 构建混合检索器BM25 + 向量检索
        HybridDocumentRetriever hybridRetriever = new HybridDocumentRetriever(elasticSearchService, vectorStore);
        // TODO 其他检索器 图数据库或者ES检索器

        // 返回检索器列表
        return List.of(sqlRetriever, hybridRetriever);
    }
}
