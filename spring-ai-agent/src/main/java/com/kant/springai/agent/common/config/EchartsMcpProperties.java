package com.kant.springai.agent.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** MCP-ECharts 连接配置，Token 通过 MCP_ECHARTS_TOKEN 环境变量提供。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mcp.echarts")
public class EchartsMcpProperties {

    private String url = "https://mcp.api-inference.modelscope.net/9857ad6fdb8242/mcp";

    private String token = "ms-37a8f407-4b4e-48d2-9327-dca5b98bdd4a";

    private Duration requestTimeout = Duration.ofSeconds(30);
}
