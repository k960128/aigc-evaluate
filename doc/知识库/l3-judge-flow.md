# L3 Judge 裁判层流程图

## 完整评测流水线

```mermaid
flowchart TD
    A["Execution_MQ 单样本消息"] --> B["读取 eval_result_detail"]
    B --> C["MODEL_CALL 模型调用节点"]
    C --> D["L1 字面量/词库判定节点"]
    D --> E{"L1 是否明确拦截"}
    E -->|是| F["样本 isSafe=false, status=AUTO_SCORED"]
    F --> G["L1 节点 BLOCKED, 批次进度推进"]

    E -->|否| H["L2 ES + PGVector / Mock 召回"]
    H --> I["按 targetRiskDetailsId 过滤召回范围"]
    I --> J["RRF 按 featureId 融合"]
    J --> K["Reranker 分数回填"]
    K --> L["riskDetailsId 小类聚合与阈值路由"]
    L --> M{"L2 decision"}

    M -->|UNSAFE| N["样本 isSafe=false, status=AUTO_SCORED"]
    N --> O["L2 节点 BLOCKED, 批次进度推进"]

    M -->|SAFE 或 DEGRADED_SAFE| P["样本 isSafe=true, status=AUTO_SCORED"]
    P --> Q["L2 节点 PASSED, 批次进度推进"]

    M -->|PASS_TO_L3| R["L2 节点 PASSED"]
    R --> S["L3 Judge 裁判层节点"]
    S --> T{"L3 decision"}

    T -->|UNSAFE| U["样本 isSafe=false, status=AUTO_SCORED"]
    U --> V["L3 节点 BLOCKED, 批次进度推进"]

    T -->|SAFE| W["样本 isSafe=true, status=AUTO_SCORED"]
    W --> X["L3 节点 PASSED, 批次进度推进"]

    T -->|NEED_MANUAL_REVIEW 或 DEGRADED_MANUAL_REVIEW| Y["样本 isSafe=null, status=MANUAL_REVIEWED"]
    Y --> Z["L3 节点 PASSED, 批次进度推进"]

    S -->|执行异常| AA["样本 isSafe=null, status=MANUAL_REVIEWED"]
    AA --> AB["L3 节点 FAILED, 批次进度推进"]
```

## L3 内部裁判流程

```mermaid
flowchart TD
    A["接收 L2 PASS_TO_L3 上下文"] --> B{"是否存在 targetRiskDetailsId"}
    B -->|否| C["DEGRADED_MANUAL_REVIEW"]
    C --> D["进入人工核验"]

    B -->|是| E["加载 risk_detail_rule"]
    E --> F{"规则是否存在且启用"}
    F -->|否| G["NEED_MANUAL_REVIEW"]
    G --> D

    F -->|是| H["构造 L3 Judge Prompt"]
    H --> I["调用 L3JudgeClient"]
    I --> J{"JudgeClient 类型"}
    J -->|默认降级| K["DEGRADED_MANUAL_REVIEW"]
    K --> D

    J -->|真实模型| L["调用 judgeChatClient"]
    L --> L1["提取最外层 JSON 对象"]
    L1 --> L2["校验 decision / confidence / riskDetailsId / evidence"]
    L2 -->|解析失败或字段非法| K
    L2 --> M{"riskDetailsId 是否等于 targetRiskDetailsId"}
    M -->|否| O["L3 NEED_MANUAL_REVIEW"]
    M -->|是| N{"Judge decision"}
    N -->|SAFE| P["L3 SAFE"]
    N -->|NEED_MANUAL_REVIEW| O
    N -->|UNSAFE| Q["L3 UNSAFE"]
```

## L3 节点日志结构

```mermaid
flowchart LR
    A["L3 input_snapshot"] --> A1["inputText"]
    A --> A2["modelOutput"]
    A --> A3["targetRiskDetailsId"]
    A --> A4["l2Decision / l2RouteReason"]
    A --> A5["l2RiskDetailHits"]
    A --> A6["riskDetailRule"]

    B["L3 output_snapshot"] --> B1["decision"]
    B --> B2["confidence"]
    B --> B3["reason"]
    B --> B4["evidence"]
    B --> B5["rawResponse"]
    B --> B6["degraded"]
    B --> B7["riskDetailRuleSnapshot"]

    C["L3 node_result"] --> C1["decision"]
    C --> C2["safe"]
    C --> C3["score"]
    C --> C4["riskDetailsId"]
    C --> C5["targetRiskDetailsId"]
    C --> C6["routeReason"]
```

## 当前阶段行为

- L3 只在 L2 `PASS_TO_L3` 时执行。
- 默认 `app.l3.judge-mode=real`，使用 `JudgeConfiguration` 中的 `judgeChatClient` 调用真实裁判大模型。
- 如需回到纯降级链路，可配置 `app.l3.judge-mode=default`，使用 `DefaultL3JudgeClient` 返回 `DEGRADED_MANUAL_REVIEW`。
- 模型调用异常、非 JSON、字段非法或证据不足时，样本进入 `MANUAL_REVIEWED`，批次进度正常推进。
- 即使后续真实 Judge 返回 `UNSAFE`，如果 `riskDetailsId` 与 `targetRiskDetailsId` 不一致，也会转为人工核验。
