package com.kant.llm.eval.common.config;

import com.kant.llm.eval.common.condition.ConditionalOnL2RealSearchEnabled;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.extern.slf4j.Slf4j;

/**
 * L2 真实召回相关配置。
 *
 * <p>只有 app.l2.recall-mode=real 或 app.l2.index-sync.enabled=true 时才会注册。
 * 密码等敏感配置只参与客户端构造，不写入日志。</p>
 */
@Slf4j
@Configuration
@ConditionalOnL2RealSearchEnabled
public class L2RealRecallConfiguration {

    @Bean
    public RestClient l2ElasticsearchRestClient(L2EsProperties properties) {
        HttpHost[] hosts = properties.getUris().stream()
                .filter(StringUtils::isNotBlank)
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);
        log.info("创建 L2 ES RestClient，hostCount: {}, authEnabled: {}, connectTimeout: {}, socketTimeout: {}",
                hosts.length, StringUtils.isNotBlank(properties.getUsername()),
                properties.getConnectTimeout(), properties.getSocketTimeout());
        RestClientBuilder builder = RestClient.builder(hosts)
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(properties.getConnectTimeout())
                        .setSocketTimeout(properties.getSocketTimeout()));
        if (StringUtils.isNotBlank(properties.getUsername())) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(
                            properties.getUsername(),
                            StringUtils.defaultString(properties.getPassword())));
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }
        return builder.build();
    }

    @Bean
    public ElasticsearchClient l2ElasticsearchClient(RestClient l2ElasticsearchRestClient) {
        log.info("创建 L2 ElasticsearchClient");
        RestClientTransport transport = new RestClientTransport(
                l2ElasticsearchRestClient,
                new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
