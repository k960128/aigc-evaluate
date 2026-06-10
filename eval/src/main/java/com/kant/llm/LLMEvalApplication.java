package com.kant.llm;

import com.kant.llm.eval.common.config.L2EsProperties;
import com.kant.llm.eval.common.config.L2KnowledgeIndexSyncProperties;
import com.kant.llm.eval.common.config.L2RecallModeProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({
        L2RecallModeProperties.class,
        L2EsProperties.class,
        L2KnowledgeIndexSyncProperties.class
})
@SpringBootApplication(exclude = {
        PgVectorStoreAutoConfiguration.class,
        MilvusVectorStoreAutoConfiguration.class
})
@MapperScan(basePackages = {
        "com.kant.llm.*.dao.mapper"
})
public class LLMEvalApplication {
    public static void main(String[] args) {
        SpringApplication.run(LLMEvalApplication.class, args);
    }
}
