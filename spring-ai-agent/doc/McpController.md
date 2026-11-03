# MCP-ECharts Client

`spring-ai-agent` 使用 Spring AI 1.1.0 所管理的 MCP SDK，通过 Streamable HTTP 连接 MCP-ECharts。客户端首次调用时完成 MCP 握手，应用关闭时释放连接。

## 配置

启动 `eval` 应用前设置 `MCP_ECHARTS_TOKEN` 环境变量，值为 ModelScope Token 本身，不包含 `Bearer ` 前缀。Token 不写入源码或配置文件。

```powershell
$env:MCP_ECHARTS_TOKEN = '<你的 ModelScope Token>'
```

也可在应用外部配置中设置以下属性：

```yaml
mcp:
  echarts:
    url: https://mcp.api-inference.modelscope.net/9857ad6fdb8242/mcp
    token: ${MCP_ECHARTS_TOKEN:}
    request-timeout: 30s
```

URL 和请求超时已有上述默认值。没有配置 Token 时应用仍可启动，调用 MCP 接口时才会校验配置。所有 MCP 请求均携带 `Authorization: Bearer <Token>`。

## 连通性测试

应用默认端口为 `8800`，上下文路径为 `/api/aigc-eval`：

```bash
curl http://localhost:8800/api/aigc-eval/mcp/test
```

`GET /mcp/test` 调用真实 MCP `tools/list`。成功时响应 `code` 为 `0`，`data.tools` 包含服务端工具名称、说明和输入 JSON Schema。这会验证连接、Bearer 鉴权、MCP 初始化和工具发现，不依赖大模型。

## 调用工具

根据测试接口返回的工具名称和 `inputSchema`，传入实际工具参数：

```http
POST /api/aigc-eval/mcp/call-tool
Content-Type: application/json

{
  "name": "<data.tools 中的工具名称>",
  "arguments": {}
}
```

`arguments` 按对应工具的 `inputSchema` 填写；无参数工具可省略。正常执行返回 `code: "0"` 和 MCP 原始结果。工具返回 `isError: true` 时，接口返回 `code: "MCP_TOOL_ERROR"` 并保留工具错误内容。

## 自动验证

在仓库根目录执行：

```powershell
mvn.cmd -o -pl spring-ai-agent -am test '-Dtest=McpControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

本地测试使用真实 MCP SDK 和进程内 HTTP 服务，覆盖 Bearer 请求头、完整端点路径、握手、会话传递、工具发现、工具调用、错误结果和空名称校验，同时确认未配置 Token 时 Starter 不会提前初始化专用 Client。

配置 `MCP_ECHARTS_TOKEN` 后，同一测试类还会启用真实远端连通性测试；未配置时跳过该项。2026-11-03 已通过所给 ModelScope 服务的远端测试，成功发现 18 个工具。

该服务在关闭会话的 `DELETE` 请求上返回 `405`，当前 SDK 会记录关闭警告；不影响握手、工具发现或测试结果。
