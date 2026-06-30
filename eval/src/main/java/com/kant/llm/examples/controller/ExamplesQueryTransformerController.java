package com.kant.llm.examples.controller;

import com.alibaba.fastjson2.JSON;
import com.kant.llm.agent.chat.service.ChatMessageService;
import com.kant.llm.rag.moduler.EvaSqlDatabaseContentRetriever;
import com.kant.llm.rag.moduler.EvalQueryRouter;
import com.kant.llm.rag.moduler.EvalQueryTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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
        List<DocumentRetriever> documentRetrievers = new ArrayList<>();
        documentRetrievers.add(new EvaSqlDatabaseContentRetriever());
        documentRetrievers.add(VectorStoreDocumentRetriever.builder().vectorStore(vectorStore).build());

        // 执行检索路由
        EvalQueryRouter evalQueryRouter = new EvalQueryRouter(documentRetrievers, chatClient);
        List<DocumentRetriever> route = evalQueryRouter.route(new Query(query));
        return JSON.toJSONString(route);
    }
}
