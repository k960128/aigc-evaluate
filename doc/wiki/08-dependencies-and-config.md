# Dependencies And Config

## Maven 模块

父项目 `pom.xml`：

- `packaging=pom`
- 只声明一个模块：`eval`
- Java 版本：`21`
- Spring Boot Parent：`3.5.9`
- Spring AI BOM：`1.1.0`

后端模块 `eval/pom.xml` 包含当前主要依赖。

## 核心依赖

| 依赖 | 用途 |
| --- | --- |
| `spring-boot-starter-web` | REST API |
| `spring-boot-starter-validation` | 参数校验 |
| `spring-boot-starter-test` | 测试 |
| `mybatis-plus-spring-boot3-starter` | ORM 和 Mapper |
| `mybatis-plus-jsqlparser` | MyBatis-Plus 分页等 SQL 解析能力 |
| `mysql-connector-java` | MySQL 数据库连接 |
| `spring-boot-starter-data-redis` | Redis |
| `redisson-spring-boot-starter` | 分布式锁 |
| `rocketmq-spring-boot-starter` | RocketMQ |
| `spring-ai-deepseek` | DeepSeek 模型调用 |
| `spring-ai-openai` | OpenAI 兼容模型调用 |
| `spring-ai-client-chat` | Spring AI Chat 抽象 |
| `spring-ai-alibaba-dashscope` | DashScope/Qwen 相关能力 |
| `spring-ai-zhipuai` | GLM/智谱模型调用 |
| `aho-corasick-double-array-trie` | AC 自动机风险词匹配 |
| `hanlp` | NLP/AC 相关依赖 |
| `sa-token-spring-boot3-starter` | 登录认证 |
| `sa-token-redis-template` | Sa-Token Redis 支持 |
| `hutool-all` | Snowflake ID 等工具 |
| `fastjson2` | 节点快照 JSON 序列化 |
| `lombok` | 简化 POJO |

## 应用配置

配置文件：`eval/src/main/resources/application.yml`

| 配置段 | 说明 |
| --- | --- |
| `server.port` | 当前为 `8800` |
| `server.servlet.context-path` | 当前为 `/api/aigc-eval` |
| `spring.application.name` | 当前为 `aigc-eval` |
| `spring.datasource` | MySQL 连接和 HikariCP 配置 |
| `spring.data.redis` | Redis host、port、database 等 |
| `rocketmq` | RocketMQ name-server 和 producer group |
| `sa-token` | token 名称、过期时间、并发登录等 |
| `app.demo-mode` | 当前为 `false` |

## MyBatis-Plus 配置

路径：`common/config/DataBaseConfiguration.java`

配置内容：

- 注册 `MybatisPlusInterceptor`。
- 添加 `PaginationInnerInterceptor(DbType.MYSQL)`。
- 注册 `MyMetaObjectHandler`。

自动填充逻辑：

- `createTime`：插入时填充。
- `updateTime`：插入和更新时填充。
- `deleted`：按实体上的 `@TableLogic` 支持逻辑删除。

## Redis 依赖关系

Redis 当前用于：

- Redisson 分布式锁：
  - 任务操作锁：`aigc-eval:eval-task:operation:{taskId}`
  - 样本执行锁：`aigc-eval:eval-result:execute:{resultDetailId}`
  - 风险词发布锁：`RiskVocabularyAcRedisKeys.PUBLISH_LOCK`
- AC 自动机快照：
  - latest version
  - latest hash
  - versioned snapshot
  - Pub/Sub channel
- Sa-Token Redis 支持。

## RocketMQ 依赖关系

Topic 常量在 `EvalMqTopics`：

| Topic | 生产者 | 消费者 | 说明 |
| --- | --- | --- | --- |
| `EvalTaskSplit_MQ` | `EvalTaskMqProducer#sendTaskSplitMessage` | `EvalTaskSplitConsumer` | 将一次任务批次拆成样本结果明细 |
| `Execution_MQ` | `EvalTaskMqProducer#sendSampleExecutionMessage` | `EvalSampleExecutionConsumer` | 执行单条样本评测 |

消费者组：

- `aigc-eval-task-split-consumer-group`
- `aigc-eval-sample-execution-consumer-group`

## 模型策略依赖关系

```text
EvalSampleExecutionConsumer
  -> ModelInfoMapper
  -> ModelClientStrategyFactory
  -> ModelClientStrategy
  -> vendor strategy
  -> external LLM API
```

策略工厂支持：

- OpenAI
- DeepSeek
- Qwen
- Spark
- GLM
- GPT

注意：

- `SPARK` 策略类存在，但当前需要检查其方法是否真正实现。
- `TELE`、`KIMI` 只在枚举中存在，策略工厂未接入。

## 安全配置提示

当前 `application.yml` 中包含明文数据库、Redis、RocketMQ 连接信息。建议：

- 使用环境变量注入敏感配置。
- 为本地开发提供 `application-local.yml`，并加入 `.gitignore`。
- 不在文档、日志、接口响应中暴露 API Key、密码、连接串。
- `ModelInfoVO` 当前返回 `apiKey`，建议后续脱敏或按权限控制。
