# Key Classes And Functions

## `LLMEvalApplication`

路径：`eval/src/main/java/com/kant/llm/LLMEvalApplication.java`

Spring Boot 启动入口。启用调度能力并扫描 Mapper。

关键点：

- `@EnableScheduling`
- `@SpringBootApplication`
- `@MapperScan(basePackages = "com.kant.llm.*.dao.mapper")`

## `EvalTaskController`

路径：`eval/src/main/java/com/kant/llm/eval/controller/EvalTaskController.java`

评测任务 API 入口。

关键方法：

| 方法 | HTTP | 说明 |
| --- | --- | --- |
| `createEvalTask` | `POST /eval-task/create` | 调用创建任务逻辑；当前 Service 未真正落库 |
| `submitEvalTask` | `POST /eval-task/submit` | 发起评测批次 |
| `stopEvalTask` | `POST /eval-task/stop` | 停止当前活跃批次 |
| `pageEvalTask` | `POST /eval-task/page` | 分页查询任务定义，并附带最近批次摘要 |
| `pageEvalTaskDetail` | `POST /eval-task/detail/page` | 查询任务执行批次 |
| `pageEvalResultDetail` | `POST /eval-task/result/page` | 查询样本级结果 |
| `getEvalTaskStatus` | `GET /eval-task/status` | 查询当前/最近批次状态 |
| `listPipelineNodeLogs` | `GET /eval-task/pipeline-node/list` | 查询单样本流水线节点日志 |
| `getEvalTaskProgress` | `GET /eval-task/progress` | 当前为空实现 |

## `EvalTaskServiceImpl`

路径：`eval/src/main/java/com/kant/llm/eval/service/impl/EvalTaskServiceImpl.java`

评测任务核心业务服务。

### `createEvalTask(CreateEvalTaskRequest request)`

当前仅记录日志，没有插入 `eval_task`。这是当前最明显的任务创建缺口。

### `submitEvalTask(Long taskId)`

职责：

- 防重复提交。
- 校验任务存在。
- 校验无活跃批次。
- 校验数据集非空。
- 创建 `EvalTaskDetailDO`。
- 投递任务拆分 MQ。

重要依赖：

- `EvalTaskMapper`
- `DataSetSampleMapper`
- `EvalTaskDetailMapper`
- `EvalTaskMqProducer`
- `RedissonClient`

### `stopEvalTask(Long taskId)`

职责：

- 停止当前活跃批次。
- 保留已完成样本结果。
- 将未执行样本标记为 `STOPPED`。

### `pageEvalTask(...)`

分页查询任务定义，同时批量查询模型、数据集、最近批次，避免逐条查库。

### `pageEvalResultDetail(...)`

分页查询样本结果，并补充样本引用信息和是否存在流水线日志。

### `getEvalTaskStatus(Long taskId)`

优先返回活跃批次状态；没有活跃批次时返回最近批次；从未提交时返回未提交语义。

### `listPipelineNodeLogs(Long resultDetailId)`

按 `startTime`、`createTime`、`id` 正序返回节点执行历史。

## `EvalTaskMqProducer`

路径：`eval/src/main/java/com/kant/llm/eval/mq/producer/EvalTaskMqProducer.java`

封装 RocketMQ 发送。

方法：

- `sendTaskSplitMessage(EvalTaskSplitMessage message)`：发送到 `EvalTaskSplit_MQ`。
- `sendSampleExecutionMessage(EvalSampleExecutionMessage message)`：发送到 `Execution_MQ`。

## `EvalTaskSplitConsumer`

路径：`eval/src/main/java/com/kant/llm/eval/mq/consumer/EvalTaskSplitConsumer.java`

任务拆分消费者。

### `onMessage(EvalTaskSplitMessage message)`

职责：

- 接收一次执行批次。
- 加载数据集样本。
- 幂等生成 `EvalResultDetailDO`。
- 将批次推进到 `READY`。
- 投递单样本执行消息。

### `splitSample(EvalTaskDetailDO taskDetail, DataSetSampleDO sample)`

为一个样本生成结果明细。通过查询已有记录和捕获 `DuplicateKeyException` 处理重复消费。

### `resendPendingExecutionMessages(EvalTaskDetailDO taskDetail)`

重复收到拆分消息且批次已进入 `READY/RUNNING` 时，补发仍处于 `PENDING` 状态的样本执行消息。

## `EvalSampleExecutionConsumer`

路径：`eval/src/main/java/com/kant/llm/eval/mq/consumer/EvalSampleExecutionConsumer.java`

单样本执行消费者，是当前评测闭环中最重要的执行类。

### `onMessage(EvalSampleExecutionMessage message)`

职责：

- 对 `resultDetailId` 加分布式锁。
- 跳过终态样本。
- 调用 `executeSample`。
- 成功或失败后更新结果并推进进度。

### `executeSample(...)`

核心业务：

- 检查批次是否停止。
- 将批次从 `READY` 推进为 `RUNNING`。
- 加载模型配置。
- 通过策略调用被测模型。
- 记录 `MODEL_CALL` 节点。
- 执行 L1 判定。
- 生成待落库结果。

### `loadModelInfo(Long modelId)`

从 `model_info` 查询模型配置，并转换为模型调用层 `ModelInfo`。会将 `manufacturerCode` 转换为 `ModelManufacturerEnum`。

### `applyL1Judgement(...)`

执行 L1 安全判定：

- 调用 `L1InterceptionEngine#analyze`。
- 拦截时写入 `isSafe=false` 和错误信息。
- 通过时写入 `isSafe=true`。
- 记录 L1 节点状态。

### `increaseTaskProgress(...)`

用 SQL 原子递增 `finished_count`，失败时递增 `failed_count`，随后刷新批次最终状态。

## `EvalPipelineNodeRecorder`

路径：`eval/src/main/java/com/kant/llm/eval/service/EvalPipelineNodeRecorder.java`

流水线节点日志记录组件。

方法：

| 方法 | 说明 |
| --- | --- |
| `startNode` | 插入节点开始记录，状态为 `RUNNING` |
| `finishNode` | 正常完成节点，可传入 `PASSED` 或 `BLOCKED` 等状态 |
| `failNode` | 标记节点失败 |
| `skipNode` | 标记节点跳过 |
| `stopNode` | 标记节点因批次停止而终止 |

所有快照对象通过 Fastjson2 序列化为 JSON 字符串存储。

## `L1InterceptionEngine`

路径：`eval/src/main/java/com/kant/llm/eval/engine/L1InterceptionEngine.java`

L1 字面量风险拦截引擎。

### `init()`

启动时初始化 AC 自动机：

1. 优先从 Redis latest 快照加载。
2. 失败时从 DB 中 `syncStatus=true` 且未删除的风险词构建。

### `analyze(String prompt)`

使用当前 `AcAutomatonContext` 扫描文本。

- `riskLevel=1`：标记 L1 blocked 并抛出 `SecurityBlockException`。
- `riskLevel=2`：加入 warning tags。
- 未命中：返回未拦截的 `EvalContext`。

### `rebuildFromPublishMessage(RiskVocabularyPublishMessage message)`

从 Redis Pub/Sub 消息加载指定快照，校验版本、hash、词条数，构建新 Trie，并通过 `AtomicReference` 原子替换当前上下文。

## `RiskVocabularyKeywordServiceImpl`

路径：`eval/src/main/java/com/kant/llm/eval/service/impl/RiskVocabularyKeywordServiceImpl.java`

风险词发布服务。

### `publishAcSnapshot()`

职责：

- 加发布锁。
- 全量读取未删除风险词。
- 构建快照和 hash。
- 写入 Redis 快照、latest version、latest hash。
- 发布 Redis Pub/Sub 消息。
- 将 DB 词条标记为已同步。

## `ModelClientStrategyFactory`

路径：`eval/src/main/java/com/kant/llm/eval/client/ModelClientStrategyFactory.java`

模型策略工厂。

### `getStrategy(ModelInfo modelInfo)`

通过 `strategyMap.compute(modelInfo.getModelId(), ...)` 创建策略。当前实现每次 compute 都调用 `createStrategy(modelInfo)`，不会复用传入的旧 strategy。

### `createStrategy(ModelInfo modelInfo)`

根据 `manufacturerType` switch 创建策略：

- `OPENAI` -> `OpenAiModelClientStrategy`
- `DEEPSEEK` -> `DeepSeekModelClientStrategy`
- `QWEN` -> `QwenModelClientStrategy`
- `SPARK` -> `SparkModelClientStrategy`
- `GLM` -> `GlmModelClientStrategy`
- `GPT` -> `GptModelClientStrategy`

`TELE`、`KIMI` 当前会进入 default 并抛出 `ServiceException`。

## `SourceController`

模型资源入口，负责：

- 模型厂商 CRUD。
- 模型配置 CRUD。
- 模型连通性测试。

注意：`ModelInfoVO` 当前会返回 `apiKey` 字段，存在敏感信息暴露风险。

## `UserServiceImpl`

认证服务。`login` 使用用户名查询用户并直接比较密码，成功后调用 `StpUtil.login(userDO.getId())`。当前未看到密码哈希校验逻辑。
