package com.kant.ai.infra.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;

import javax.net.ssl.SSLContext;

/**
 * infra-ai 通用 Elasticsearch 客户端配置。
 *
 * <p>这里只提供底层 ElasticsearchClient，不包含任何 L2 索引名、字段映射或风险业务语义。</p>
 */
@Slf4j
@AutoConfiguration
public class InfraElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:}")
    private String uris;

    @Value("${spring.elasticsearch.username:}")
    private String username;

    @Value("${spring.elasticsearch.password:}")
    private String password;

    @Value("${spring.elasticsearch.insecure:false}")
    private boolean insecure;

    @Bean
    @ConditionalOnExpression("'${spring.elasticsearch.uris:}'.trim().length() > 0")
    public ElasticsearchClient elasticsearchClient() {
        RestClientBuilder builder = RestClient.builder(HttpHost.create(uris));
        configureAuthenticationAndSsl(builder);
        RestClient restClient = builder.build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(buildObjectMapper()));
        return new ElasticsearchClient(transport);
    }

    /**
     * 构建 ES Java API Client 使用的 Jackson Mapper。
     *
     * <p>默认 JacksonJsonpMapper 内部 ObjectMapper 不一定注册 JavaTimeModule。
     * 评测结果和流水线节点投影里有 LocalDateTime 字段，如果不注册 JavaTimeModule，
     * 写入 ES 时会抛出 “Java 8 date/time type not supported by default”。</p>
     */
    private ObjectMapper buildObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private void configureAuthenticationAndSsl(RestClientBuilder builder) {
        if (username != null && !username.isEmpty()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                configureInsecureSslIfNeeded(httpClientBuilder);
                return httpClientBuilder;
            });
            return;
        }
        if (uris.startsWith("https") && insecure) {
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                configureInsecureSslIfNeeded(httpClientBuilder);
                return httpClientBuilder;
            });
        }
    }

    private void configureInsecureSslIfNeeded(org.apache.http.impl.nio.client.HttpAsyncClientBuilder httpClientBuilder) {
        if (!uris.startsWith("https") || !insecure) {
            return;
        }
        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();
            httpClientBuilder
                    .setSSLContext(sslContext)
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create SSLContext for ES client", ex);
        }
    }
}
