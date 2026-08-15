package com.kant.ai.infra.vector;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PGVector 配置属性。
 */
@Data
@ConfigurationProperties(prefix = "rag.vector.pg")
public class PgVectorProperties {

    private String driverClassName;

    private String url;

    private String username;

    private String password;

    private boolean initializeSchema;

    private String schemaName;

    private String tableName;

    private int dimensions;

    private int maxDocumentBatchSize;
}
