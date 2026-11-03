package com.kant.springai.agent.common.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

/** MCP-ECharts Streamable HTTP 客户端，首次调用时才连接远端服务。 */
@Configuration(proxyBeanMethods = false)
public class EchartsMcpConfiguration {

    // 专用 Client 仅通过 Qualifier 注入，避免被 Starter 收集后在启动时提前初始化。
    @Bean(name = "echartsMcpClient", destroyMethod = "closeGracefully", defaultCandidate = false)
    @Lazy
    public McpSyncClient echartsMcpClient(EchartsMcpProperties properties) {
        Assert.hasText(properties.getToken(),
                "请配置 MCP_ECHARTS_TOKEN 环境变量或 mcp.echarts.token 后再调用 MCP 接口");
        Assert.hasText(properties.getUrl(), "mcp.echarts.url 不能为空");
        Assert.notNull(properties.getRequestTimeout(), "mcp.echarts.request-timeout 不能为空");
        Assert.isTrue(properties.getRequestTimeout().isPositive(),
                "mcp.echarts.request-timeout 必须大于 0");

        WebClientStreamableHttpTransport transport = WebClientStreamableHttpTransport.builder(
                        WebClient.builder().defaultHeaders(
                                headers -> headers.setBearerAuth(properties.getToken().trim())))
                .endpoint(properties.getUrl())
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("spring-ai-agent-echarts", "1.0.0"))
                .requestTimeout(properties.getRequestTimeout())
                .build();
        try {
            client.initialize();
            return client;
        } catch (RuntimeException ex) {
            client.closeGracefully();
            throw ex;
        }
    }
}
