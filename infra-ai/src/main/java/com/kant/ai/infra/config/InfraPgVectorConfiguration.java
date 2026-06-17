package com.kant.ai.infra.config;

import com.kant.ai.infra.embedding.EmbeddingService;
import com.kant.ai.infra.vector.PgVectorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * infra-ai 通用 PGVector 配置。
 *
 * <p>这里只提供 PG 数据源、JdbcTemplate 和 VectorStore，不包含 L2 metadata、riskDetailsId 等业务语义。</p>
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(PgVectorProperties.class)
public class InfraPgVectorConfiguration {

    @Bean(name = "pgJdbcTemplate")
    public JdbcTemplate pgJdbcTemplate(PgVectorProperties properties) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "vectorStore")
    public VectorStore vectorStore(@Qualifier("pgJdbcTemplate") JdbcTemplate jdbcTemplate,
                                   PgVectorProperties properties,
                                   EmbeddingService embeddingService) {
        int embeddingDimension = embeddingService.dimension();
        if (embeddingDimension > 0 && embeddingDimension != properties.getDimensions()) {
            log.warn("PGVector 表维度与默认 Embedding 模型维度不一致，pgDimensions: {}, embeddingDimension: {}",
                    properties.getDimensions(), embeddingDimension);
        }
        return PgVectorStore.builder(jdbcTemplate, embeddingService.getEmbeddingClient())
                .dimensions(properties.getDimensions())
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(properties.isInitializeSchema())
                .schemaName(properties.getSchemaName())
                .vectorTableName(properties.getTableName())
                .maxDocumentBatchSize(properties.getMaxDocumentBatchSize())
                .build();
    }
}
