# Known Gaps And Risks

## 当前实现缺口

| 缺口 | 影响 |
| --- | --- |
| `EvalTaskServiceImpl#createEvalTask` 只记录日志，未插入 `eval_task` | API 层存在创建入口，但无法通过该接口完成最小评测任务创建 |
| `EvalTaskController#createEvalTask` 返回 `void` | 与统一 `Result<T>` 响应风格不一致 |
| `EvalTaskController#getEvalTaskProgress` 方法体为空 | `/eval-task/progress` 不提供真实进度 |
| L2、L3 节点只在枚举中预留 | 当前安全判定只完成模型调用和 L1 风险词拦截 |
| `SparkModelClientStrategy` 需要确认实际实现 | 若方法返回空或未调用外部 API，会导致 Spark 评测不可用 |
| `TELE`、`KIMI` 未接入策略工厂 | 枚举存在但不能执行评测 |
| 当前仓库未发现前端模块 | 只能确认后端能力，不能按当前仓库说明前端实现 |

## 数据和契约风险

| 风险 | 说明 |
| --- | --- |
| `ModelInfoVO` 返回 `apiKey` | 前端或调用方可能看到完整模型密钥 |
| `application.yml` 包含明文连接信息 | 数据库、Redis、RocketMQ 敏感配置应迁移 |
| 登录直接比较明文密码 | `UserServiceImpl#login` 未看到密码 hash/加盐校验 |
| `createEvalTask` 与后续提交链路不闭环 | 用户无法只通过 API 从创建走到提交 |
| `ModelClientStrategyFactory#getStrategy` 使用 `modelId` 作为 map key | 连通性测试若传空 `modelId` 可能触发空 key 相关问题，需要结合运行验证 |
| 策略工厂 `compute` 每次都创建新策略 | `strategyMap` 当前没有真正复用已有 strategy 的效果 |

## 运行风险

| 风险 | 说明 |
| --- | --- |
| RocketMQ 是评测链路必要依赖 | 提交任务后如果 MQ 不可用，任务无法拆分执行 |
| Redis 是锁和 AC 快照依赖 | Redis 不可用会影响提交锁、执行锁、风险词快照加载 |
| 外部模型 API 不稳定 | 单样本执行可能失败并推进 `failedCount` |
| 批次停止无法中断已发出的模型 HTTP 请求 | 只能在模型返回后跳过后续成功落库 |

## 文本编码问题

源码中大量中文注释在当前读取环境显示为乱码。建议后续统一检查文件编码，确保 Java 源文件、Markdown 文档和 IDE 配置均为 UTF-8。

## 建议优先级

1. 补齐 `createEvalTask` 落库逻辑，并统一返回 `Result<EvalTaskVO>` 或任务 ID。
2. 实现 `/eval-task/progress` 或删除该空接口，避免前端误用。
3. 模型 API Key 脱敏返回，敏感配置迁移到环境变量或本地 profile。
4. 为 `submitEvalTask`、`EvalTaskSplitConsumer`、`EvalSampleExecutionConsumer` 增加单元/集成测试。
5. 明确 `Spark`、`TELE`、`KIMI` 的支持状态，未支持时在 API 层给出清晰错误。
6. 将密码校验改为安全 hash 校验。
7. 完成 L2/L3 或在产品文档中明确当前版本仅支持 L1。
8. 建立数据库迁移体系，确保 `eval_pipeline_node_detail`、`eval_result_detail.task_detail_id` 等结构可追踪。

## 后续可扩展方向

- L2：接入 ES + Milvus 双路召回，记录召回节点日志。
- L3：接入 Judge LLM，对疑似样本进行认知裁决。
- 报告：按任务批次聚合安全率、拦截率、失败率、风险类别分布。
- 人工复核：将 `AUTO_SCORED` 结果流转到 `MANUAL_REVIEWED`。
- 权限：补齐 RBAC、模型密钥访问权限、操作审计。
