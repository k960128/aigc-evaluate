package com.kant.springai.agent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kant.springai.agent.common.config.EchartsMcpConfiguration;
import com.kant.springai.agent.common.config.EchartsMcpProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.StdioTransportAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.client.webflux.autoconfigure.StreamableHttpWebFluxTransportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用真实 MCP SDK 和本地 Streamable HTTP 服务验证完整调用链路。 */
class McpControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ReceivedRequest> requests = new CopyOnWriteArrayList<>();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    StdioTransportAutoConfiguration.class,
                    StreamableHttpWebFluxTransportAutoConfiguration.class,
                    McpClientAnnotationScannerAutoConfiguration.class,
                    McpClientAutoConfiguration.class,
                    McpToolCallbackAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    private HttpServer server;
    private boolean toolError;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void startsWithoutTokenAndDoesNotInitializeDedicatedClient() {
        contextRunner.withPropertyValues("mcp.echarts.token=").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(McpController.class);
            assertThat(context.getSourceApplicationContext().getBeanFactory()
                    .containsSingleton("echartsMcpClient")).isFalse();
        });
    }

    @Test
    void testEndpointInitializesAuthenticatedClientAndListsRemoteTools() throws IOException {
        localContext().run(context -> {
            mvc(context.getBean(McpController.class)).perform(get("/mcp/test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.tools[0].name").value("generate_chart"));
            assertThat(requests).extracting(ReceivedRequest::method)
                    .contains("initialize", "notifications/initialized", "tools/list");
            assertThat(requests).allSatisfy(request -> {
                assertThat(request.path()).isEqualTo("/test-server/mcp");
                assertThat(request.authorization()).isEqualTo("Bearer test-token");
                if (!request.method().equals("initialize")) {
                    assertThat(request.sessionId()).isEqualTo("test-session");
                }
            });
        });
    }

    @Test
    void callsToolWithOriginalArgumentsAndReturnsContent() throws IOException {
        localContext().run(context -> {
            mvc(context.getBean(McpController.class)).perform(post("/mcp/call-tool")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"generate_chart","arguments":{"title":"销售趋势"}}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.content[0].text").value("chart-created"));
            ReceivedRequest call = requests.stream()
                    .filter(request -> request.method().equals("tools/call")).findFirst().orElseThrow();
            assertThat(call.body().path("params").path("name").asText()).isEqualTo("generate_chart");
            assertThat(call.body().path("params").path("arguments").path("title").asText())
                    .isEqualTo("销售趋势");
        });
    }

    @Test
    void returnsFailureCodeWhenToolReportsError() throws IOException {
        toolError = true;
        localContext().run(context -> {
            mvc(context.getBean(McpController.class)).perform(post("/mcp/call-tool")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"generate_chart\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("MCP_TOOL_ERROR"))
                    .andExpect(jsonPath("$.data.isError").value(true));
            ReceivedRequest call = requests.stream()
                    .filter(request -> request.method().equals("tools/call")).findFirst().orElseThrow();
            assertThat(call.body().path("params").path("arguments").isEmpty()).isTrue();
        });
    }

    @Test
    void rejectsBlankToolNameWithoutConnecting() {
        contextRunner.withPropertyValues("mcp.echarts.token=").run(context -> {
            mvc(context.getBean(McpController.class)).perform(post("/mcp/call-tool")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\" \"}"))
                    .andExpect(status().isBadRequest());
            assertThat(context.getSourceApplicationContext().getBeanFactory()
                    .containsSingleton("echartsMcpClient")).isFalse();
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MCP_ECHARTS_TOKEN", matches = ".+")
    void connectsToConfiguredRemoteServer() {
        contextRunner.withPropertyValues(
                "mcp.echarts.token=" + System.getenv("MCP_ECHARTS_TOKEN")).run(context -> {
            mvc(context.getBean(McpController.class)).perform(get("/mcp/test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.tools").isArray());
        });
    }

    private ApplicationContextRunner localContext() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/test-server/mcp", this::handle);
        server.start();
        return contextRunner.withPropertyValues(
                "mcp.echarts.url=http://127.0.0.1:" + server.getAddress().getPort() + "/test-server/mcp",
                "mcp.echarts.token=test-token", "mcp.echarts.request-timeout=5s");
    }

    private MockMvc mvc(McpController controller) {
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"Bearer test-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(exchange.getRequestMethod().equals("DELETE") ? 200 : 405, -1);
                return;
            }
            JsonNode request = mapper.readTree(exchange.getRequestBody());
            String method = request.path("method").asText();
            requests.add(new ReceivedRequest(method, exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Mcp-Session-Id"), request));
            if (!request.has("id")) {
                exchange.sendResponseHeaders(202, -1);
                return;
            }
            String result = switch (method) {
                case "initialize" -> """
                        {"protocolVersion":"2025-06-18","capabilities":{"tools":{}},
                         "serverInfo":{"name":"test-echarts","version":"1.0.0"}}
                        """;
                case "tools/list" -> """
                        {"tools":[{"name":"generate_chart","description":"Generate a chart",
                         "inputSchema":{"type":"object","properties":{"title":{"type":"string"}}}}]}
                        """;
                case "tools/call" -> "{\"content\":[{\"type\":\"text\",\"text\":\"chart-created\"}],\"isError\":"
                        + toolError + "}";
                default -> "{}";
            };
            ObjectNode response = mapper.createObjectNode().put("jsonrpc", "2.0");
            response.set("id", request.get("id"));
            response.set("result", mapper.readTree(result));
            byte[] body = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Mcp-Session-Id", "test-session");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private record ReceivedRequest(String method, String path, String authorization,
                                   String sessionId, JsonNode body) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EchartsMcpProperties.class)
    @Import({EchartsMcpConfiguration.class, McpController.class})
    static class TestConfiguration {
    }
}
