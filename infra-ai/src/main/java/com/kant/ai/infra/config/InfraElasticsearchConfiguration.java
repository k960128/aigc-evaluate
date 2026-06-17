package com.kant.ai.infra.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
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

    @Value("${spring.elasticsearch.uris}")
    private String uris;

    @Value("${spring.elasticsearch.username:}")
    private String username;

    @Value("${spring.elasticsearch.password:}")
    private String password;

    @Value("${spring.elasticsearch.insecure:false}")
    private boolean insecure;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        try {
            RestClientBuilder builder = RestClient.builder(HttpHost.create(uris));
            configureAuthenticationAndSsl(builder);
            RestClient restClient = builder.build();
            ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            return new ElasticsearchClient(transport);
        } catch (Exception ex) {
            log.warn("Failed to create Elasticsearch client: {}. ES functionality will be unavailable.", ex.getMessage(), ex);
            return null;
        }
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
