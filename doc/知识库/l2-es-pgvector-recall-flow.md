# L2 真实 ES + PGVector 混合召回流程

## 知识库同步链路

```mermaid
flowchart TD
    A["前端维护 risk_attack_feature"] --> B["MySQL 保存知识事实"]
    B --> C["sync_status / es_sync_status / pg_sync_status = 待同步"]
    C --> D["一键同步 /risk/l2/kb/sync"]
    D --> E["L2KnowledgeSyncService 拆分待同步特征"]
    E --> F["生成 kb_sync_event"]
    F --> G["RocketMQ 投递单条同步事件"]
    G --> H["KbSyncEventConsumer 消费事件"]
    H --> I{"operationType"}
    I -->|REINDEX| J["写入 ES: _id = featureId, featureId = MySQL id"]
    I -->|REINDEX| K["写入 PGVector: Document.id = 稳定 UUID, metadata.featureId = MySQL id"]
    I -->|DELETE| L["删除 ES 文档"]
    I -->|DELETE| M["删除 PGVector 文档"]
    J --> N["回写 es_status"]
    K --> O["回写 pg_status"]
    L --> N
    M --> O
    N --> P["回写 risk_attack_feature 同步状态"]
    O --> P
```

## L2 评测执行链路

```mermaid
flowchart TD
    A["EvalSampleExecutionConsumer"] --> B["L1 检测通过"]
    B --> C["L2EvaluationService.evaluate"]
    C --> D["构造 queryText: 用户输入 + 模型输出 + L1 warning"]
    D --> E{"app.l2.recall-mode"}
    E -->|mysql-mock| F["MySqlMockL2RecallClient"]
    E -->|empty| G["DefaultL2RecallClient"]
    E -->|real| H["EsPgL2RecallClient"]
    H --> I["ES BM25 召回 risk_attack_feature"]
    H --> J["PGVector 语义召回 vector_store"]
    I --> K["映射 ES 命中为 L2FeatureHit.featureId"]
    J --> L["从 metadata.featureId 映射 PGVector 命中"]
    K --> M["L2RecallResult.esHits"]
    L --> N["L2RecallResult.milvusHits 兼容承载 PGVector"]
    F --> O["RRF 按 featureId 融合"]
    G --> O
    M --> O
    N --> O
    O --> P["Reranker / 降级精排"]
    P --> Q["按 riskDetailsId 聚合风险小类"]
    Q --> R{"阈值路由"}
    R -->|UNSAFE| S["自动拦截 AUTO_SCORED"]
    R -->|SAFE| T["自动通过 AUTO_SCORED"]
    R -->|PASS_TO_L3| U["进入人工核验"]
    S --> V["写 eval_pipeline_node_detail"]
    T --> V
    U --> V
```

## ID 对齐约定

```text
MySQL:
  risk_attack_feature.id = 统一 featureId

ES:
  _id = String.valueOf(featureId)
  featureId = MySQL risk_attack_feature.id

PGVector:
  Document.id = UUID.nameUUIDFromBytes("l2-risk-attack-feature:" + featureId)
  metadata.featureId = MySQL risk_attack_feature.id

RRF:
  只按 L2FeatureHit.featureId 合并，不按 ES _id 或 PG Document.id 合并
```
