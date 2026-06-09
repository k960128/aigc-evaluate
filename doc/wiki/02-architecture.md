# Architecture

## 整体架构

当前后端采用典型 Spring Boot 分层结构，并通过 RocketMQ 解耦评测任务的批量执行。

```mermaid
flowchart TD
    Client["API Client / Frontend"] --> Controller["Controller Layer"]
    Controller --> Service["Service Layer"]
    Service --> Mapper["MyBatis-Plus Mapper"]
    Mapper --> DB[("MySQL")]

    Service --> Producer["EvalTaskMqProducer"]
    Producer --> SplitMQ["RocketMQ: EvalTaskSplit_MQ"]
    Producer --> ExecMQ["RocketMQ: Execution_MQ"]

    SplitMQ --> SplitConsumer["EvalTaskSplitConsumer"]
    SplitConsumer --> DB
    SplitConsumer --> Producer

    ExecMQ --> ExecConsumer["EvalSampleExecutionConsumer"]
    ExecConsumer --> StrategyFactory["ModelClientStrategyFactory"]
    StrategyFactory --> ModelStrategy["Vendor Strategy"]
    ModelStrategy --> LLM["External LLM API"]

    ExecConsumer --> L1["L1InterceptionEngine"]
    L1 --> Redis[("Redis AC Snapshot")]
    L1 --> DB

    ExecConsumer --> Recorder["EvalPipelineNodeRecorder"]
    Recorder --> DB
```

## 分层说明

| 层 | 主要包 | 职责 |
| --- | --- | --- |
| API 层 | `controller` | 暴露 REST API，做请求参数接收和响应包装 |
| 业务层 | `service`, `service.impl` | 任务提交、查询、停止、风险词发布等业务逻辑 |
| 持久层 | `dao.entity`, `dao.mapper` | MyBatis-Plus 实体和 Mapper |
| 模型调用层 | `client`, `client.strategy` | 统一模型请求/响应对象和厂商策略 |
| 评测引擎 | `engine` | L1 风险词拦截和 AC 自动机热更新 |
| 消息层 | `mq` | RocketMQ topic、消息体、生产者、消费者 |
| 公共基础设施 | `common` | 统一响应、异常、枚举、配置、Web 工具 |

## 请求链路

普通查询和管理接口走同步链路：

```mermaid
sequenceDiagram
    participant C as Client
    participant API as Controller
    participant S as Service
    participant M as Mapper
    participant DB as MySQL

    C->>API: HTTP Request
    API->>S: call service
    S->>M: query/update
    M->>DB: SQL
    DB-->>M: rows
    M-->>S: DO
    S-->>API: VO/Page/Boolean
    API-->>C: Result<T>
```

## 评测执行链路

评测任务提交后进入异步链路：

```mermaid
sequenceDiagram
    participant API as EvalTaskController
    participant S as EvalTaskServiceImpl
    participant P as EvalTaskMqProducer
    participant MQ1 as EvalTaskSplit_MQ
    participant Split as EvalTaskSplitConsumer
    participant MQ2 as Execution_MQ
    participant Exec as EvalSampleExecutionConsumer
    participant LLM as Model Provider
    participant L1 as L1InterceptionEngine
    participant DB as MySQL

    API->>S: submitEvalTask(taskId)
    S->>DB: create eval_task_detail
    S->>P: sendTaskSplitMessage
    P->>MQ1: publish split message
    MQ1->>Split: consume
    Split->>DB: create eval_result_detail per sample
    Split->>MQ2: publish execution messages
    MQ2->>Exec: consume per sample
    Exec->>LLM: call target model
    Exec->>L1: analyze(modelOutput)
    Exec->>DB: update result, node logs, task progress
```

## 风险词库发布与热更新

```mermaid
flowchart TD
    API["POST /risk/vocabularies/keyword/version/publish"] --> Service["RiskVocabularyKeywordServiceImpl.publishAcSnapshot"]
    Service --> DB[("Query risk_vocabulary_keyword")]
    Service --> Snapshot["Build RiskVocabularySnapshot"]
    Snapshot --> RedisWrite["Redis MULTI/EXEC: snapshot/latest/hash/publish"]
    RedisWrite --> Listener["RiskVocabularyAcMessageListener"]
    Listener --> Engine["L1InterceptionEngine.rebuildFromPublishMessage"]
    Engine --> Active["AtomicReference<AcAutomatonContext>"]
```

应用启动时，`L1InterceptionEngine#init` 会优先从 Redis latest 快照加载 AC 自动机；Redis 快照不可用时，从 DB 中 `syncStatus=true` 的风险词兜底构建。

## 状态流转

评测批次状态：

```mermaid
stateDiagram-v2
    [*] --> CREATING
    CREATING --> INITIALIZING
    INITIALIZING --> READY
    READY --> RUNNING
    RUNNING --> COMPLETED
    RUNNING --> ERROR
    CREATING --> STOPPED
    INITIALIZING --> STOPPED
    READY --> STOPPED
    RUNNING --> STOPPED
```

样本结果状态：

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> AUTO_SCORED
    PENDING --> FAILED
    PENDING --> STOPPED
    AUTO_SCORED --> MANUAL_REVIEWED
```
