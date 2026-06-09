# Domain Model

## 评测任务域

评测任务由三层数据构成：

| 表 / Entity | 说明 | 关键字段 |
| --- | --- | --- |
| `eval_task` / `EvalTaskDO` | 评测任务定义 | `id`, `taskName`, `modelId`, `datasetId` |
| `eval_task_detail` / `EvalTaskDetailDO` | 一次任务提交产生的执行批次 | `id`, `taskId`, `serialNo`, `status`, `totalCount`, `finishedCount`, `failedCount`, `startTime`, `endTime` |
| `eval_result_detail` / `EvalResultDetailDO` | 样本级评测结果 | `id`, `taskId`, `taskDetailId`, `sampleId`, `inputText`, `modelOutput`, `rawResponse`, `latency`, `isSafe`, `status`, `errorMsg` |
| `eval_pipeline_node_detail` / `EvalPipelineNodeDetailDO` | 单样本流水线节点日志 | `taskId`, `taskDetailId`, `resultDetailId`, `sampleId`, `nodeCode`, `status`, `inputSnapshot`, `outputSnapshot`, `nodeResult` |

关系：

```text
eval_task 1 -- n eval_task_detail
eval_task_detail 1 -- n eval_result_detail
eval_result_detail 1 -- n eval_pipeline_node_detail
dataset_sample 1 -- n eval_result_detail
```

## 任务状态

`TaskStatusEnums` 描述执行批次状态：

| 枚举 | code | 含义 |
| --- | ---: | --- |
| `CREATING` | 0 | 创建中，等待拆分 |
| `INITIALIZING` | 1 | 初始化中，拆分消费者正在处理 |
| `READY` | 2 | 就绪，样本明细已生成并开始投递执行消息 |
| `RUNNING` | 3 | 进行中，样本正在执行 |
| `COMPLETED` | 4 | 已完成 |
| `ERROR` | 5 | 异常 |
| `STOPPED` | 6 | 已停止 |

## 样本结果状态

`EvalResultStatusEnums` 描述单条样本状态：

| 枚举 | code | 含义 |
| --- | ---: | --- |
| `PENDING` | 0 | 未处理，等待执行消费者处理 |
| `AUTO_SCORED` | 1 | 自动评分完成 |
| `MANUAL_REVIEWED` | 2 | 人工复核完成 |
| `FAILED` | 3 | 执行失败 |
| `STOPPED` | 4 | 已终止 |

## 流水线节点

`PipelineNodeCodeEnums`：

| 节点 | code | 当前状态 |
| --- | --- | --- |
| 模型调用 | `MODEL_CALL` | 已接入 |
| L1 字面量拦截 | `L1` | 已接入 |
| L2 双路召回 | `L2` | 预留，当前未接入业务链路 |
| L3 认知裁决 | `L3` | 预留，当前未接入业务链路 |

`PipelineNodeStatusEnums`：

| 状态 | code | 含义 |
| --- | --- | --- |
| 执行中 | `RUNNING` | 节点已开始 |
| 通过 | `PASSED` | 节点完成且通过 |
| 拦截 | `BLOCKED` | 节点命中风险或短路规则 |
| 失败 | `FAILED` | 节点执行异常 |
| 跳过 | `SKIPPED` | 因条件未执行 |
| 终止 | `STOPPED` | 用户停止批次后节点停止 |

## 数据集域

| 表 / Entity | 说明 | 关键字段 |
| --- | --- | --- |
| `dataset_info` / `DataSetDO` | 数据集基础信息 | `id`, `datasetName`, `datasetType`, `sampleCount`, `description` |
| `dataset_sample` / `DataSetSampleDO` | 数据集样本 | `id`, `datasetId`, `inputText`, `answerText`, `scoreRule`, `field` |

数据集用于给评测任务提供待测 Prompt。提交任务时，系统会按 `task.datasetId` 查询 `dataset_sample`，并为每条样本生成一条 `eval_result_detail`。

## 模型资源域

| 表 / Entity | 说明 | 关键字段 |
| --- | --- | --- |
| `model_manufacturer` / `ModelManufacturerDO` | 模型厂商 | `id`, `manufacturerName`, `manufacturerCode`, `defaultBaseUrl`, `enable` |
| `model_info` / `ModelInfoDO` | 模型配置 | `id`, `model`, `baseUrl`, `apiKey`, `manufacturerCode`, `maxThreadSize`, `maxCompletionTokens`, `stream`, `status`, `config`, `version` |

`ModelManufacturerEnum` 当前枚举：

```text
OPENAI, DEEPSEEK, QWEN, TELE, SPARK, GLM, KIMI, GPT
```

`ModelClientStrategyFactory` 当前支持创建：

```text
OPENAI, DEEPSEEK, QWEN, SPARK, GLM, GPT
```

`TELE` 和 `KIMI` 在枚举中存在，但当前策略工厂未接入。

## 风险知识域

| 表 / Entity | 说明 | 关键字段 |
| --- | --- | --- |
| `risk_category` / `RiskCategoryDO` | 风险分类 | `id`, `categoryName`, `sortOrder`, `status` |
| `risk_details` / `RiskDetailsDO` | 风险明细 | `id`, `categoryId`, `detailsName`, `sortOrder`, `status` |
| `risk_scenario` / `RiskScenarioDO` | 风险场景 | `id`, `majorCategoryId`, `scenarioCode`, `scenarioName`, `judgeRule`, `severityLevel`, `status` |
| `risk_vocabulary_keyword` / `RiskVocabularyKeywordDO` | 风险词特征 | `id`, `groupId`, `riskDetailsId`, `keyword`, `riskLevel`, `matchType`, `syncStatus` |

风险词关键语义：

| 字段 | 含义 |
| --- | --- |
| `riskLevel=1` | 致命级别，命中后 L1 直接拦截 |
| `riskLevel=2` | 疑似级别，命中后作为 warning tag 透传 |
| `matchType=1` | 精确匹配 |
| `matchType=2` | 模糊/包含匹配，当前 AC 构建逻辑以快照转换为准 |
| `syncStatus=true` | 词条已进入某次 AC 发布快照 |

## 用户域

| 表 / Entity | 说明 |
| --- | --- |
| `t_user` / `UserDO` | 用户认证数据 |

`UserServiceImpl#login` 按用户名查询用户，并直接比较明文密码，成功后调用 Sa-Token 登录。
