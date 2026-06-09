# Module Responsibilities

## 根模块

| 路径 | 职责 |
| --- | --- |
| `pom.xml` | Maven 父项目，声明 Java 21、Spring Boot、Spring AI BOM 和 `eval` 模块 |
| `eval/pom.xml` | 后端模块依赖，包含 Web、MyBatis-Plus、Redis、RocketMQ、Spring AI、Sa-Token 等 |
| `eval/src/main/resources/application.yml` | 服务端口、Context Path、数据库、Redis、RocketMQ、Sa-Token 等运行配置 |

## `controller`

Controller 层负责暴露 REST API，通常调用 Service 或 MyBatis-Plus Service，并通过 `Results.success(...)` 返回统一响应。

| Controller | Base Path | 职责 |
| --- | --- | --- |
| `AuthController` | `/auth` | 登录、登出 |
| `SourceController` | `/source` | 模型厂商、模型配置、模型连通性测试 |
| `DataSetController` | `/data-set` | 数据集 CRUD 和样本查询 |
| `EvalTaskController` | `/eval-task` | 评测任务提交、停止、分页、状态、结果、流水线日志 |
| `RiskVocabularyController` | `/risk/vocabularies` | 风险词 CRUD、分页、AC 快照发布 |
| `RiskScenarioController` | `/risk/scenarios` | 风险场景 CRUD、分页 |
| `RiskCategoryController` | `/risk/category` | 风险分类和风险明细管理 |
| `TestController` | `/test` | 模型策略和 L1 分析测试入口 |

## `service` 和 `service.impl`

业务服务分两类：

- 继承 MyBatis-Plus `IService` 的简单 CRUD 服务。
- 承载复杂业务的专用服务，例如 `EvalTaskService` 和 `EvalPipelineNodeRecorder`。

| Service | 职责 |
| --- | --- |
| `EvalTaskService` / `EvalTaskServiceImpl` | 评测任务提交、停止、分页查询、结果查询、状态计算、流水线日志查询 |
| `EvalPipelineNodeRecorder` | 记录模型调用、L1 等节点的开始、完成、失败、跳过、停止状态 |
| `RiskVocabularyKeywordService` / `RiskVocabularyKeywordServiceImpl` | 风险词 CRUD 和 AC 自动机快照发布 |
| `UserService` / `UserServiceImpl` | 用户登录校验和 Sa-Token 登录态创建 |
| `DataSetService`, `DataSetSampleService` | 数据集和样本基础 CRUD |
| `ModelInfoService`, `ModelManufacturerService` | 模型配置和厂商基础 CRUD |
| `RiskCategoryService`, `RiskDetailsService`, `RiskScenarioService` | 风险分类、明细、场景基础管理 |

## `dao.entity` 和 `dao.mapper`

持久层使用 MyBatis-Plus：

- Entity 使用 `@TableName` 绑定表。
- Mapper 继承 `BaseMapper<T>`。
- `MyMetaObjectHandler` 自动填充 `createTime`、`updateTime`、`deleted`。

主要实体：

- `EvalTaskDO`
- `EvalTaskDetailDO`
- `EvalResultDetailDO`
- `EvalPipelineNodeDetailDO`
- `DataSetDO`
- `DataSetSampleDO`
- `ModelInfoDO`
- `ModelManufacturerDO`
- `RiskVocabularyKeywordDO`
- `RiskCategoryDO`
- `RiskDetailsDO`
- `RiskScenarioDO`
- `UserDO`

## `client` 和 `client.strategy`

模型调用层定义统一接口和厂商策略：

| 类 | 职责 |
| --- | --- |
| `ModelClientStrategy` | 模型调用统一接口，包含 `call`、`connection`、`getManufacturer` |
| `ModelClientStrategyFactory` | 根据 `ModelInfo.manufacturerType` 创建对应策略 |
| `ModelInfo` | 模型运行时配置对象 |
| `ModelRequest` | 模型调用请求，包含模型信息和输入文本 |
| `ModelResponse` | 模型调用响应，包含输出内容和耗时 |
| `ModelConnectionResponse` | 连通性测试响应 |

当前策略类：

- `OpenAiModelClientStrategy`
- `DeepSeekModelClientStrategy`
- `QwenModelClientStrategy`
- `GlmModelClientStrategy`
- `GptModelClientStrategy`
- `SparkModelClientStrategy`

## `engine`

评测引擎当前主要是 L1 字面量风险拦截：

| 类 | 职责 |
| --- | --- |
| `L1InterceptionEngine` | 维护当前生效 AC 自动机，扫描模型输出并返回/抛出拦截结果 |
| `RiskVocabularyAcMessageListener` | 监听 Redis Pub/Sub 风险词发布消息，触发 AC 重建 |
| `RiskVocabularyAcSupport` | 将风险词转换为快照项、计算 hash、构建 Trie |
| `AcAutomatonContext` | 当前 AC 自动机上下文 |
| `RiskTag` | Trie value，保存风险明细、等级等信息 |
| `RiskVocabularySnapshot` | Redis 中保存的版本快照 |

## `mq`

消息层把评测执行拆成两个异步阶段：

| 类 | 职责 |
| --- | --- |
| `EvalMqTopics` | Topic 常量：`EvalTaskSplit_MQ`、`Execution_MQ` |
| `EvalTaskMqProducer` | 发送任务拆分消息和单样本执行消息 |
| `EvalTaskSplitMessage` | 任务批次拆分消息体 |
| `EvalSampleExecutionMessage` | 单样本执行消息体 |
| `EvalTaskSplitConsumer` | 消费任务拆分消息，生成样本结果明细并投递执行消息 |
| `EvalSampleExecutionConsumer` | 消费单样本执行消息，调用模型、执行 L1、写回结果 |

## `common`

| 子包 | 职责 |
| --- | --- |
| `common.config` | Web、Redis、MyBatis-Plus 配置 |
| `common.convention` | `Result<T>`、`EvalContext` |
| `common.database` | MyBatis-Plus 自动填充处理器 |
| `common.enums` | 任务、结果、流水线节点、模型厂商等枚举 |
| `common.errorcode` | 错误码定义 |
| `common.exception` | 业务异常、客户端异常、远程异常、安全拦截异常 |
| `common.web` | 统一响应工具、全局异常处理、SSE 发送器 |
| `common.constant` | Redis Key 常量 |
| `common.splitter` | 文本切分工具 |
