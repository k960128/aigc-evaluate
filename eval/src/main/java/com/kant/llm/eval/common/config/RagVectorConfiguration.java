package com.kant.llm.eval.common.config;

import com.kant.llm.eval.embedding.EmbeddingModelClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * RAG vector store configuration.
 *
 * <p>This class does not expose the PG DataSource as a bean, so the primary MySQL DataSource remains unchanged.</p>
 */
@Configuration
@EnableConfigurationProperties(RagVectorProperties.class)
public class RagVectorConfiguration {

    @Bean(name = "ragPgVectorJdbcTemplate")
    @ConditionalOnProperty(prefix = "rag.vector", name = "type", havingValue = "pg", matchIfMissing = true)
    public JdbcTemplate ragPgVectorJdbcTemplate(RagVectorProperties properties) {
        RagVectorProperties.Pg pg = properties.getPg();
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(pg.getDriverClassName());
        dataSource.setUrl(pg.getUrl());
        dataSource.setUsername(pg.getUsername());
        dataSource.setPassword(pg.getPassword());
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "ragVectorStore")
    @Lazy
    @ConditionalOnProperty(prefix = "rag.vector", name = "type", havingValue = "pg", matchIfMissing = true)
    public VectorStore ragPgVectorStore(@Qualifier("ragPgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
                                        EmbeddingModelClient embeddingModelClient,
                                        RagVectorProperties properties) {
        RagVectorProperties.Pg pg = properties.getPg();
        return PgVectorStore.builder(jdbcTemplate, embeddingModelClient.getEmbeddingModel())
                .dimensions(pg.getDimensions())
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(pg.isInitializeSchema())
                .schemaName(pg.getSchemaName())
                .vectorTableName(pg.getTableName())
                .maxDocumentBatchSize(pg.getMaxDocumentBatchSize())
                .build();
    }

    @Bean(name = "ragMilvusServiceClient")
    @Lazy
    @ConditionalOnProperty(prefix = "rag.vector", name = "type", havingValue = "milvus")
    public MilvusServiceClient ragMilvusServiceClient(RagVectorProperties properties) {
        RagVectorProperties.Milvus milvus = properties.getMilvus();
        return new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(milvus.getHost())
                .withPort(milvus.getPort())
                .build());
    }

    @Bean(name = "ragVectorStore")
    @Lazy
    @ConditionalOnProperty(prefix = "rag.vector", name = "type", havingValue = "milvus")
    public VectorStore ragMilvusVectorStore(@Qualifier("ragMilvusServiceClient") MilvusServiceClient milvusClient,
                                            EmbeddingModelClient embeddingModelClient,
                                            RagVectorProperties properties) {
        RagVectorProperties.Milvus milvus = properties.getMilvus();
        return MilvusVectorStore.builder(milvusClient, embeddingModelClient.getEmbeddingModel())
                .collectionName(milvus.getCollectionName())
                .databaseName(milvus.getDatabaseName())
                .indexType(IndexType.IVF_FLAT)
                .metricType(MetricType.COSINE)
                .embeddingDimension(milvus.getEmbeddingDimension())
                .initializeSchema(milvus.isInitializeSchema())
                .build();
    }
}
