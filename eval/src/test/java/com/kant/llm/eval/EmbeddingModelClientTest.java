package com.kant.llm.eval;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class EmbeddingModelClientTest {

    @Autowired
    private VectorStore vectorStore;

    @Test
    public void embed(){
        EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(OpenAiApi.builder()
                .apiKey("sk-xxxxxxxx9")
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .build(),
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model("text-embedding-v4")
                        .dimensions(1024)
                        .build());

        float[] embed = embeddingModel.embed("衣服的质量杠杠的，很漂亮，不枉我等了这么久啊，喜欢，以后还来这里买");
        log.info("embeddingModel = {}", embed);
    }
}
