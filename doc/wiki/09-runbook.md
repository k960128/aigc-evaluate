# Runbook

## 环境要求

| 工具/服务 | 说明 |
| --- | --- |
| JDK 21 | 项目 Java 版本 |
| Maven | 构建和测试 |
| MySQL | 主数据库 |
| Redis | 分布式锁、AC 快照、Sa-Token 支持 |
| RocketMQ | 评测任务拆分和样本执行 |
| 外部 LLM API | 模型连通性测试和样本执行需要 |

## 构建

在项目根目录执行：

```bash
mvn clean package
```

只构建后端模块：

```bash
mvn clean package -pl eval
```

## 运行

启动后端：

```bash
mvn spring-boot:run -pl eval
```

默认访问前缀：

```text
http://localhost:8800/api/aigc-eval
```

## 测试

运行 `eval` 模块测试：

```bash
mvn test -pl eval
```

运行单个测试类：

```bash
mvn test -pl eval -Dtest=ModelClientStrategyTest
```

运行单个测试方法：

```bash
mvn test -pl eval -Dtest=ModelClientStrategyTest#test
```

当前测试目录中可见：

- `ModelClientStrategyTest`
- RAG 相关实验测试

## 运行前检查

启动前确认：

1. MySQL 可连接，且表结构已创建。
2. Redis 可连接。
3. RocketMQ name-server 可连接。
4. 对应 RocketMQ topic 可自动创建或已提前创建。
5. 模型配置中的 `apiKey`、`baseUrl`、`manufacturerCode` 正确。
6. 风险词库需要 L1 命中时，已发布 AC 快照或 DB 中有 `syncStatus=true` 的风险词。

## 最小评测链路

当前可用闭环需要数据库中已有任务定义：

1. 创建或准备模型厂商和模型配置。
2. 创建或准备数据集和样本。
3. 准备 `eval_task` 记录，绑定 `modelId` 和 `datasetId`。
4. 调用 `POST /eval-task/submit?taskId=...`。
5. `EvalTaskSplitConsumer` 消费拆分消息。
6. `EvalSampleExecutionConsumer` 消费样本执行消息。
7. 查询：
   - `POST /eval-task/page`
   - `GET /eval-task/status?taskId=...`
   - `POST /eval-task/result/page`
   - `GET /eval-task/pipeline-node/list?resultDetailId=...`

注意：`POST /eval-task/create` 当前不会真正写入 `eval_task`。

## 风险词发布流程

1. 通过 `/risk/vocabularies/keyword/create` 或数据库准备风险词。
2. 调用：

```text
POST /api/aigc-eval/risk/vocabularies/keyword/version/publish
```

3. 服务会写入 Redis 快照并发布通知。
4. `RiskVocabularyAcMessageListener` 收到通知后触发 `L1InterceptionEngine` 重建本地 AC 自动机。

## 常见排查点

### 提交任务失败

检查：

- `eval_task` 是否存在。
- `dataset_sample` 中是否存在对应 `datasetId` 的样本。
- 是否已有活跃批次。
- Redis 锁是否可用。
- RocketMQ 发送是否失败。

### 任务一直不执行

检查：

- `EvalTaskSplit_MQ` 是否有消费者。
- `Execution_MQ` 是否有消费者。
- RocketMQ name-server 配置是否正确。
- 消费者组是否启动。
- 日志中是否有消息发送失败。

### 样本执行失败

检查：

- `model_info` 是否存在。
- `manufacturerCode` 是否能转换为 `ModelManufacturerEnum`。
- 策略工厂是否支持该厂商。
- `apiKey`、`baseUrl`、`model` 是否正确。
- 外部模型 API 是否可访问。

### L1 没有命中

检查：

- 风险词是否存在且未删除。
- 是否发布过 AC 快照。
- Redis latest version/hash 是否存在。
- `L1InterceptionEngine` 启动日志是否成功加载 Redis 或 DB。
- 风险词 `riskLevel` 和 `matchType` 是否符合预期。

### 任务停止后仍看到模型调用

模型 HTTP 调用无法被强制中断。当前逻辑会在调用返回后再次检查批次是否停止，如果已停止，会跳过后续 L1 和最终成功结果落库。

## 推荐本地配置方式

建议不要直接修改已提交的 `application.yml`。更稳妥的方式是：

```bash
mvn spring-boot:run -pl eval -Dspring-boot.run.profiles=local
```

然后使用本地未提交的 `application-local.yml` 覆盖敏感连接信息。
