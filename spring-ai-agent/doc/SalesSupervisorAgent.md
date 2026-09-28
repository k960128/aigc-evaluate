# Sales Supervisor Agent Demo

这个 Demo 展示一个循环式的 Supervisor 多 Agent 工作流。Supervisor 不持有任何销售业务工具，
它根据用户目标和已有执行结果，逐轮决定调用哪一个专业 Worker，直到返回 `FINISH`。

## 结构

```text
SalesMultiAgentController
        |
        v
SalesSupervisorAgent（路由、交接、结束判断、汇总）
        |
        +--> SalesDataAgent     （订单、汇总、排名工具）
        +--> SalesInsightAgent  （同比环比、趋势、异常工具）
        +--> SalesChartAgent    （ECharts 图表工具）
```

三个 Worker 可以共享同一个底层模型，但各自拥有独立的 `ChatClient`、系统提示词和工具权限。
一次请求内的执行记录作为显式共享状态传回 Supervisor，不使用跨会话记忆。

## 配置

Demo 不在源码中保存 API Key。可以在运行应用的配置中加入：

```yaml
sales-agent:
  multi-agent:
    api-key: ${SALES_AGENT_MULTI_AGENT_API_KEY:}
    base-url: https://dashscope.aliyuncs.com/compatible-mode
    model: qwen3.8-max
    max-delegations: 6
```

也可以直接通过 Spring Boot 环境变量 `SALES_AGENT_MULTI_AGENT_API_KEY` 提供密钥。
模型会在第一次调用接口时延迟创建，因此不配置该 Demo 不会阻止应用启动。

## 调用示例

运行入口位于 `eval` 模块。全局 context path 为 `/api/aigc-eval`，完整请求示例：

```bash
curl -X POST "http://localhost:8080/api/aigc-eval/sales/multi-agent/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"分析今年各大区销售表现，找出异常，并生成柱状图"}'
```

响应中的 `steps` 会展示 Supervisor 实际选择的 Agent、委派指令、Worker 输出和耗时；
`result.answer` 是 Supervisor 汇总后的最终回答。Supervisor 最多交接
`max-delegations` 次，避免模型在 Worker 之间无限循环。
