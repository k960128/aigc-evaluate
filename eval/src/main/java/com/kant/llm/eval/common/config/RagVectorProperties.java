package com.kant.llm.eval.common.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;

import java.util.Locale;

/**
 * RAG vector store properties.
 */
@Data
@ConfigurationProperties(prefix = "rag.vector")
public class RagVectorProperties implements InitializingBean {

    private VectorType type = VectorType.PG;

    private Pg pg = new Pg();

    private Milvus milvus = new Milvus();

    public void setType(String type) {
        this.type = VectorType.from(type);
    }

    @Override
    public void afterPropertiesSet() {
        if (type == null) {
            throw new IllegalArgumentException("rag.vector.type must be pg or milvus");
        }
    }

    public boolean isPg() {
        return VectorType.PG.equals(type);
    }

    public boolean isMilvus() {
        return VectorType.MILVUS.equals(type);
    }

    public enum VectorType {
        PG,
        MILVUS;

        public static VectorType from(String value) {
            if (StringUtils.isBlank(value)) {
                return PG;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "pg" -> PG;
                case "milvus" -> MILVUS;
                default -> throw new IllegalArgumentException("Unsupported rag.vector.type: " + value + ", expected pg or milvus");
            };
        }
    }

    @Data
    public static class Pg {

        private String url;

        private String username;

        private String password;

        private String driverClassName = "org.postgresql.Driver";

        private boolean initializeSchema = false;

        private String schemaName = "public";

        private String tableName = "vector_store";

        private int dimensions = 1536;

        private int maxDocumentBatchSize = 1000;
    }

    @Data
    public static class Milvus {

        private String host = "127.0.0.1";

        private int port = 19530;

        private String databaseName = "default";

        private String collectionName = "vector_store";

        private boolean initializeSchema = false;

        private int embeddingDimension = 1536;
    }
}
