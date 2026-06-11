package com.kant.llm.eval;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
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
    public void embedAndSave(){
        List<Document> documents = new ArrayList<>();
        documents.add(new Document("衣服的质量杠杠的，很漂亮，不枉我等了这么久啊，喜欢，以后还来这里买"));
        vectorStore.add(documents);
    }

    /**
     * 测试向量搜索
     */
    @Test
    public void vectorSearch(){
        List<Document> documents = vectorStore.similaritySearch("衣服质量如何");
        log.info("documents = {}", documents);
    }
}
