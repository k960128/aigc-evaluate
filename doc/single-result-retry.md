# 单样本重试接口

`POST /eval-task/result/retry`，沿用现有登录及统一响应格式。

```json
{
  "resultDetailId": 123,
  "mode": "AUTO",
  "requestId": "retry-123-20261113-1"
}
```

- `resultDetailId` 必填，指定本次批次中的结果明细。
- `mode` 缺省为 `AUTO`。有有效模型输出则重跑 L1/L2/L3；无输出则从模型调用开始。`FULL` 清空输出并重跑完整链路。
- `requestId` 必填，1 至 64 位字母、数字、连字符或下划线。请求超时或结果不确定时使用原 ID 重试；用户主动发起下一次评测时使用新 ID。

响应的 `data`：

```json
{
  "resultDetailId": 123,
  "executionVersion": 1,
  "startStage": "SAFETY_EVALUATE",
  "requestId": "retry-123-20261113-1",
  "status": "ACCEPTED"
}
```

`ACCEPTED` 表示受理成功，不表示评测完成。继续通过结果分页接口及流水线节点接口查询，两个接口均返回执行轮次。相同请求 ID 始终返回原受理轮次；跨样本或跨模式复用请求 ID 会被拒绝。

## 状态规则

- 最近一次批次支持重试：批次可为就绪、执行中、完成或异常；不能同时存在其他活跃批次。
- 已停止的批次、已停止的样本及历史批次不支持重试。
- 执行锁被模型调用或安全评测持有时拒绝重试。中断后已释放执行锁的中间态可以重试。
- 已排队且没有失败信息的样本复用当前轮次和实际起始阶段，即使传入 `FULL` 也不会中断已经排队的任务。该请求 ID 会持久化为当前轮次的别名。
- 完成数和失败数只撤销该样本已计入的贡献，总样本数不变；重试完成后只计数一次。
- 旧执行轮次的消息及结果回写无法覆盖新轮次，旧节点日志保留。

## 上线步骤

1. 暂停评测消费者。
2. 对已有 MySQL 数据库执行一次 `doc/sql/mysql/migrations/20261113_single_result_retry.sql`。全新初始化可使用已更新的 `data_free_eval_dev.sql`，无需再执行迁移。
3. 统一升级模型调用和安全评测消费者后恢复消费。两个阶段与重试入口使用同一个结果执行锁；不要混用旧版消费者。

新增 `execution_version`、`progress_counted` 和 `eval_result_retry`。迁移按旧终态回填计数标记，不主动修改历史批次汇总值；发现历史计数不一致时接口拒绝扣成负数。

重试投递使用现有 RocketMQ 事务消息。结果重置、进度回退、请求记录和 ES 同步事件在同一本地事务提交，Broker 回查通过请求记录判断是否提交。模型调用仍在数据库事务外。

## 验证

```powershell
mvn.cmd -o -pl eval -am "-Dtest=SafetyEvaluateExecuteServiceTest,SafetyEvaluateResultWriterTest,EvalResultRetryServiceTest,ModelCallExecutionV1ServiceTest,EvalResultRetryControllerTest,RetryTransactionMessagingTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-DargLine=--enable-preview" test
```

事务测试使用 H2 内存库，模型及消息基础设施使用替身；真实 MySQL、Redis 和 RocketMQ 的联调需在部署环境完成。
