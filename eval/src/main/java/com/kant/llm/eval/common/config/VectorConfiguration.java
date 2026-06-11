package com.kant.llm.eval.common.config;

import com.kant.llm.eval.embedding.EmbeddingModelClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 自动装配pg数据源和jdbcTemplate模板
 手动注册vector
 */
@Configuration
public class VectorConfiguration {

    @Value("${rag.vector.pg.driver-class-name}")
    private String driverClassName;
    @Value("${rag.vector.pg.url}")
    private String url;
    @Value("${rag.vector.pg.username}")
    private String username;
    @Value("${rag.vector.pg.password}")
    private String password;
    @Value("${rag.vector.pg.initialize-schema}")
    private boolean initializeSchema;
    @Value("${rag.vector.pg.schema-name}")
    private String schemaName;
    @Value("${rag.vector.pg.table-name}")
    private String tableName;
    @Value("${rag.vector.pg.dimensions}")
    private int dimensions;
    @Value("${rag.vector.pg.max-document-batch-size}")
    private int maxDocumentBatchSize;

    @Bean(name = "pgJdbcTemplate")
    public JdbcTemplate pgJdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "vectorStore")
    public VectorStore vectorStore(@Qualifier("pgJdbcTemplate") JdbcTemplate jdbcTemplate,
                                        EmbeddingModelClient embeddingModelClient) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModelClient.getEmbeddingModel())
                .dimensions(dimensions)
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(initializeSchema)
                .schemaName(schemaName)
                .vectorTableName(tableName)
                .maxDocumentBatchSize(maxDocumentBatchSize)
                .build();
    }
}
