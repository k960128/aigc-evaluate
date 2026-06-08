# API Entrypoints

所有接口默认加上服务上下文：

```text
/api/aigc-eval
```

## 认证

Base Path：`/auth`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/login` | 登录，返回 Sa-Token 信息 |
| `POST` | `/logout` | 登出 |

## 模型资源

Base Path：`/source`

### 模型厂商

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/manufacturer/create` | 创建模型厂商 |
| `PUT` | `/manufacturer/update` | 更新模型厂商 |
| `DELETE` | `/manufacturer/delete?id=` | 删除模型厂商 |
| `GET` | `/manufacturer/get?id=` | 查询单个模型厂商 |
| `GET` | `/manufacturer/list` | 查询全部模型厂商 |

### 模型配置

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/model/create` | 创建模型配置 |
| `PUT` | `/model/update` | 更新模型配置 |
| `DELETE` | `/model/delete?id=` | 删除模型配置 |
| `GET` | `/model/get?id=` | 查询单个模型配置 |
| `GET` | `/model/list` | 查询全部模型配置 |
| `POST` | `/model/testConnectivity` | 模型连通性测试 |

## 数据集

Base Path：`/data-set`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/create` | 创建数据集 |
| `PUT` | `/update` | 更新数据集 |
| `DELETE` | `/delete?id=` | 删除数据集 |
| `GET` | `/get?id=` | 查询数据集详情 |
| `GET` | `/list` | 查询全部数据集 |
| `POST` | `/sample/page` | 分页查询数据集样本 |
| `GET` | `/sample/list?datasetId=` | 查询指定数据集全部样本 |

## 评测任务

Base Path：`/eval-task`

| 方法 | 路径 | 说明 | 当前状态 |
| --- | --- | --- | --- |
| `POST` | `/create` | 创建评测任务 | Service 当前只记录日志，未落库 |
| `POST` | `/submit?taskId=` | 提交评测任务 | 已实现 |
| `POST` | `/stop?taskId=` | 停止当前活跃批次 | 已实现 |
| `POST` | `/page` | 分页查询评测任务 | 已实现 |
| `POST` | `/detail/page` | 分页查询执行批次 | 已实现 |
| `POST` | `/result/page` | 分页查询样本结果 | 已实现 |
| `GET` | `/status?taskId=` | 查询任务状态 | 已实现 |
| `GET` | `/pipeline-node/list?resultDetailId=` | 查询样本流水线节点日志 | 已实现 |
| `GET` | `/progress?taskId=` | 获取任务进度 | 当前方法体为空 |

## 风险词库

Base Path：`/risk/vocabularies`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/keyword/create` | 创建风险词 |
| `PUT` | `/keyword/update` | 更新风险词 |
| `DELETE` | `/keyword/delete?id=` | 删除风险词 |
| `GET` | `/keyword/get?id=` | 查询风险词详情 |
| `GET` | `/keyword/list` | 查询全部风险词 |
| `POST` | `/keyword/page` | 分页查询风险词 |
| `POST` | `/keyword/version/publish` | 发布 AC 自动机风险词快照 |

## 风险场景

Base Path：`/risk/scenarios`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/create` | 创建风险场景 |
| `PUT` | `/update` | 更新风险场景 |
| `DELETE` | `/delete?id=` | 删除风险场景 |
| `GET` | `/get?id=` | 查询风险场景详情 |
| `GET` | `/list` | 查询全部风险场景 |
| `POST` | `/page` | 分页查询风险场景 |

## 风险分类和明细

Base Path：`/risk/category`

### 风险分类

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/page` | 分页查询风险分类 |
| `GET` | `/list` | 查询风险分类列表 |
| `GET` | `/{id}` | 查询风险分类详情 |
| `POST` | `/` | 新增风险分类 |
| `PUT` | `/` | 更新风险分类 |
| `DELETE` | `/{id}` | 删除风险分类 |

### 风险明细

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/details/page` | 分页查询风险明细 |
| `GET` | `/details/list` | 查询风险明细列表 |
| `GET` | `/details/{id}` | 查询风险明细详情 |
| `POST` | `/details` | 新增风险明细 |
| `PUT` | `/details` | 更新风险明细 |
| `DELETE` | `/details/{id}` | 删除风险明细 |

## 测试入口

Base Path：`/test`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/testModelClient` | 测试模型策略调用 |
| `GET` | `/testAcAnalyze` | 测试 L1 AC 分析 |

## 统一响应

大多数接口返回 `Result<T>`：

| 字段 | 说明 |
| --- | --- |
| `code` | 响应码，成功通常为 `"0"` |
| `message` | 响应消息 |
| `data` | 业务数据 |
| `requestId` | 请求 ID，当前未看到统一填充逻辑 |

少数接口例外：

- `POST /eval-task/create` 返回 `void`。
- `GET /eval-task/progress` 返回 `void` 且当前为空实现。
