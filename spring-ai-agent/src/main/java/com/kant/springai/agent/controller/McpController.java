package com.kant.springai.agent.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/mcp")
public class McpController {

    private final McpSyncClient echartsMcpClient;

    public McpController(@Lazy @Qualifier("echartsMcpClient") McpSyncClient echartsMcpClient) {
        this.echartsMcpClient = echartsMcpClient;
    }

    /** 完成 MCP 握手并查询远端工具，验证连接、鉴权和协议调用。 */
    @GetMapping("/test")
    public Result<McpSchema.ListToolsResult> test() {
        McpSchema.ListToolsResult tools = echartsMcpClient.listTools();
        log.info("MCP-ECharts 调用成功，工具数量: {}", tools.tools().size());
        return Results.success(tools);
    }

    /** 工具名称及参数以 /mcp/test 返回的 inputSchema 为准。 */
    @PostMapping("/call-tool")
    public Result<McpSchema.CallToolResult> callTool(@Valid @RequestBody CallToolRequest request) {
        McpSchema.CallToolResult result = echartsMcpClient.callTool(
                new McpSchema.CallToolRequest(request.name(),
                        request.arguments() == null ? Map.of() : request.arguments()));
        if (Boolean.TRUE.equals(result.isError())) {
            return new Result<McpSchema.CallToolResult>()
                    .setCode("MCP_TOOL_ERROR")
                    .setMessage("MCP-ECharts 工具执行失败")
                    .setData(result);
        }
        return Results.success(result);
    }

    public record CallToolRequest(@NotBlank(message = "工具名称不能为空") String name,
                                  Map<String, Object> arguments) {
    }
}
