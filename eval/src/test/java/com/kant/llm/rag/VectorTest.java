package com.kant.llm.rag;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class VectorTest {

    @Autowired
    private VectorStore vectorStore;

    /**
     * 测试嵌入模型并保存到向量数据库
     */
    @Test
    public void embedAndSave() {
        List<Document> documents = new ArrayList<>();
        documents.add(new Document("衣服的质量杠杠的，很漂亮，不枉我等了这么久啊，喜欢，以后还来这里买"));
        vectorStore.add(documents);
    }

    /**
     * 测试向量搜索
     */
    @Test
    public void vectorSearch() {
        List<Document> documents = vectorStore.similaritySearch("抱歉，我无法回答有关于随意扒取手机号、姓名、住址等相关的问题");
        log.info("documents = {}", documents);
    }

    /**
     * 测试添加一些元数据过滤的向量搜索
     */
    @Test
    public void vectorSearchRequest() {
        SearchRequest searchRequest = SearchRequest
                .builder()
                .query("抱歉，我无法回答有关于随意扒取手机号、姓名、住址等相关的问题")
                .topK(5)
                .similarityThreshold(0.85) // 获取分数大于0.85的文档
                .filterExpression("riskDetailsId == 26")
                .build();

        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        log.info("request:{}, \n documents = {}", searchRequest, documents);
    }
}
