# Agent Chat API 接口文档

## 通用说明

- 涉及 Controller：
  - `ChatController`，基础路径：`/agent/chat`
  - `ChatConversationController`，基础路径：`/agent/chat/conversation`
  - `ChatMessageController`，基础路径：`/agent/chat/message`
- 除 `/agent/chat/send` 为 `text/event-stream` 流式响应外，其余接口均使用统一响应结构 `Result<T>`。
- 成功状态码：`code = "0"`。
- 当前接口通过 `StpUtil.getLoginIdAsString()` 获取登录用户 ID；调用方需要先完成登录态注入。
- 时间字段来自 `LocalDateTime`，实体字段标注了 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`，通常序列化为 `2026-07-03 15:30:00`。
- 当前请求 DTO 和 Query 参数未声明 `@NotNull`、`@NotBlank` 等 Bean Validation 注解；`@RequestParam` 未设置 `required = false` 时按 Spring 默认规则为必填。

### 统一响应结构

```json
{
  "code": "0",
  "message": null,
  "data": {},
  "requestId": null
}
```

| 字段名 | 数据类型 | 说明 |
|---|---|---|
| code | String | 响应状态码，`"0"` 表示成功 |
| message | String | 响应消息，成功时通常为空 |
| data | Object / Array / Boolean / String / null | 业务响应数据 |
| requestId | String | 请求追踪 ID，当前成功响应构造器未主动设置 |

## 枚举说明

### ChatConversationStatus：会话状态

| 枚举名 | 说明 |
|---|---|
| ACTIVE | 活跃状态，正常使用中 |
| ARCHIVED | 已归档，不再活跃但保留 |
| DELETED | 已删除 |

### ChatMessageType：消息类型

| 枚举名 | 说明 |
|---|---|
| USER | 用户消息 |
| ASSISTANT | AI 助手消息 |

### RetrievalSource：RAG 引用来源

| 枚举名 | 说明 |
|---|---|
| VECTOR | 向量检索 |
| KEYWORD | 关键词检索 |
| HYBRID | 混合检索 |
| RERANK | 重排序后结果 |

## 1. 流式发送聊天消息

### 接口基本信息

| 项目 | 内容 |
|---|---|
| 接口名称 | 流式发送聊天消息 |
| 请求路径 | `/agent/chat/send` |
| 请求方式 | POST |
| 响应类型 | `text/event-stream` |
| 接口描述 | 接收用户问题并返回 SSE 流。未传 `conversationId` 时自动创建新会话，保存用户消息和空助手消息，随后执行意图识别、通用对话或 RAG 对话流程。 |

### 请求参数说明

请求体：`ChatSendReq`

| 参数名 | 必填 | 参数类型 | 请求位置 | 说明 |
|---|---|---|---|---|
| conversationId | 否 | String | Body | 会话唯一标识；为空时后端自动创建新会话 |
| content | 否 | String | Body | 用户输入内容。业务上应必填；当前源码未加校验，传空可能导致后续流程异常或生成空标题 |

请求示例：

```json
{
  "conversationId": "f339f2c68d494601a7efd4f037cd7231user_001",
  "content": "如何创建一次安全评测任务？"
}
```

### 响应参数说明

该接口不返回统一 `Result<T>`，而是直接返回 SSE 文本流。流中可能包含三类内容：

```text
[PROGRESS]:正在识别您的意图...
[PROGRESS]:正在优化您的问题...
大模型逐 token 输出内容
[DONE]:f339f2c68d494601a7efd4f037cd7231user_001
```

| 数据格式 | 说明 |
|---|---|
| `[PROGRESS]:xxx` | 进度通知，用于前端展示当前处理阶段 |
| 普通文本片段 | 大模型流式输出的回答内容 |
| `[DONE]:{conversationId}` | 流结束标识，并携带本轮使用的会话 ID |

### 处理逻辑说明

1. `conversationId` 为空时，使用当前登录用户创建新会话，临时标题为 `content` 前 20 个字符。
2. 新会话创建后，后台虚拟线程异步调用标题摘要服务生成会话标题并回写。
3. 后端先保存一条用户消息，再保存一条空的助手消息。
4. 先推送意图识别进度，再根据识别结果选择通用对话或 RAG 对话。
5. 流结束前追加 `[DONE]:{conversationId}`。

## 2. 获取当前用户会话列表

### 接口基本信息

| 项目 | 内容 |
|---|---|
| 接口名称 | 获取当前用户会话列表 |
| 请求路径 | `/agent/chat/conversation/list` |
| 请求方式 | GET |
| 接口描述 | 查询当前登录用户的会话列表，按更新时间倒序返回，并排除状态为 `deleted` 的会话。 |

### 请求参数说明

无请求参数。用户 ID 从登录态中获取。

### 响应参数说明

```json
{
  "code": "0",
  "message": null,
  "data": [
    {
      "id": 1,
      "conversationId": "f339f2c68d494601a7efd4f037cd7231user_001",
      "userId": "user_001",
      "title": "如何创建一次安全评测任务？",
      "status": "ACTIVE",
      "createTime": "2026-07-03 15:30:00",
      "updateTime": "2026-07-03 15:35:00",
      "deleted": false
    }
  ],
  "requestId": null
}
```

| 字段名 | 数据类型 | 说明 |
|---|---|---|
| data | Array | 会话列表 |
| data[].id | Long | 主键 ID |
| data[].conversationId | String | 会话唯一标识 |
| data[].userId | String | 用户 ID |
| data[].title | String | 会话标题 |
| data[].status | String | 会话状态，枚举见 `ChatConversationStatus` |
| data[].createTime | String | 创建时间 |
| data[].updateTime | String | 更新时间 |
| data[].deleted | Boolean | 逻辑删除标记 |

## 3. 获取会话详情

### 接口基本信息

| 项目 | 内容 |
|---|---|
| 接口名称 | 获取会话详情 |
| 请求路径 | `/agent/chat/conversation/detail` |
| 请求方式 | GET |
| 接口描述 | 根据会话唯一标识查询会话详情，并排除状态为 `deleted` 的会话。 |

### 请求参数说明

| 参数名 | 必填 | 参数类型 | 请求位置 | 说明 |
|---|---|---|---|---|
| conversationId | 是 | String | Query | 会话唯一标识 |

### 响应参数说明

```json
{
  "code": "0",
  "message": null,
  "data": {
    "id": 1,
    "conversationId": "f339f2c68d494601a7efd4f037cd7231user_001",
    "userId": "user_001",
    "title": "如何创建一次安全评测任务？",
    "status": "ACTIVE",
    "createTime": "2026-07-03 15:30:00",
    "updateTime": "2026-07-03 15:35:00",
    "deleted": false
  },
  "requestId": null
}
```

响应字段同“获取当前用户会话列表”的单条会话对象。未查询到时，`data` 通常为 `null`。

## 4. 创建会话

### 接口基本信息

| 项目 | 内容 |
|---|---|
| 接口名称 | 创建会话 |
| 请求路径 | `/agent/chat/conversation/create` |
| 请求方式 | POST |
| 接口描述 | 为当前登录用户创建一个新会话，返回会话唯一标识。 |

### 请求参数说明

| 参数名 | 必填 | 参数类型 | 请求位置 | 说明 |
|---|---|---|---|---|
| title | 否 | String | Query | 会话标题；不传时默认使用 `新对话` |

### 响应参数说明

```json
{
  "code": "0",
  "message": null,
  "data": "f339f2c68d494601a7efd4f037cd7231user_001",
  "requestId": null
}
```

| 字段名 | 数据类型 | 说明 |
|---|---|---|
| data | String | 新创建的会话唯一标识 |

## 5. 归档会话

### 接口基本信息

| 项目 | 内容 |
|---|---|
| 接口名称 | 归档会话 |
| 请求路径 | `/agent/chat/conversation/archive` |
| 请求方式 | PUT |
| 接口描述 | 根据会话唯一标识将会话状态更新为归档状态。 |

### 请求参数说明

| 参数名 | 必填 | 参数类型 | 请求位置 | 说明 |
|---|---|---|---|---|
| conversationId | 是 | String | Query | 会话唯一标识 |

### 响应参数说明

```json
{
  "code": "0",
  "message": null,
  "data": true,
  "requestId": null
}
```

| 字段名 | 数据类型 | 说明 |
|---|---|---|
| data | Boolean | 是否更新成功 |

## 6. 删除会话

### 接口基本信息

| 项目 | 内容 |
|---|---|
| 接口名称 | 删除会话 |
| 请求路径 | `/agent/chat/conversation/delete` |
| 请求方式 | DELETE |
| 接口描述 | 根据会话唯一标识将会话状态更新为删除状态。当前实现为状态更新，不会主动删除该会话下的消息。 |

### 请求参数说明

| 参数名 | 必填 | 参数类型 | 请求位置 | 说明 |
|---|---|---|---|---|
| conversationId | 是 | String | Query | 会话唯一标识 |

### 响应参数说明

```json
{
  "code": "0",
  "message": null,
  "data": true,
  "requestId": null
}
```

| 字段名 | 数据类型 | 说明 |
|---|---|---|
| data | Boolean | 是否更新成功 |

## 7. 获取会话消息列表

### 接口基本信息

| 项目 | 内容 |
|---|---|
| 接口名称 | 获取会话消息列表 |
| 请求路径 | `/agent/chat/message/list` |
| 请求方式 | GET |
| 接口描述 | 根据会话唯一标识查询消息列表，按创建时间正序返回。 |

### 请求参数说明

| 参数名 | 必填 | 参数类型 | 请求位置 | 说明 |
|---|---|---|---|---|
| conversationId | 是 | String | Query | 会话唯一标识 |

### 响应参数说明

```json
{
  "code": "0",
  "message": null,
  "data": [
    {
      "id": 1,
      "messageId": "c9de8e0da2494c3ca502d72b8ce906cf",
      "conversationId": "f339f2c68d494601a7efd4f037cd7231user_001",
      "type": "USER",
      "content": "如何创建一次安全评测任务？",
      "transformContent": null,
      "tokenCount": null,
      "modelName": null,
      "ragReferences": null,
      "metadata": null,
      "createTime": "2026-07-03 15:30:00",
      "updateTime": "2026-07-03 15:30:00",
      "deleted": false
    }
  ],
  "requestId": null
}
```

| 字段名 | 数据类型 | 说明 |
|---|---|---|
| data | Array | 消息列表 |
| data[].id | Long | 主键 ID |
| data[].messageId | String | 消息唯一标识 |
| data[].conversationId | String | 所属会话唯一标识 |
| data[].type | String | 消息类型，枚举见 `ChatMessageType` |
| data[].content | String | 消息内容 |
| data[].transformContent | String | 改写后的内容 |
| data[].tokenCount | Integer | Token 数量 |
| data[].modelName | String | 使用的模型名称 |
| data[].ragReferences | Array | RAG 引用内容列表 |
| data[].metadata | Object | 扩展元数据 |
| data[].createTime | String | 创建时间 |
| data[].updateTime | String | 更新时间 |
| data[].deleted | Boolean | 逻辑删除标记 |

### ragReferences 字段说明

| 字段名 | 数据类型 | 说明 |
|---|---|---|
| documentId | String | 文档 ID |
| url | String | 文档 URL |
| documentTitle | String | 文档标题 |
| chunkId | String | 文档块 ID |
| chunkContent | String | 文档块内容 |
| similarityScore | Double | 相似度分数 |
| rerankScore | Double | 重排序分数 |
| retrievalSource | String | 检索来源，枚举见 `RetrievalSource` |
| metadata | Object | 扩展元数据 |

## 8. 获取消息详情

### 接口基本信息

| 项目 | 内容 |
|---|---|
| 接口名称 | 获取消息详情 |
| 请求路径 | `/agent/chat/message/detail` |
| 请求方式 | GET |
| 接口描述 | 根据消息唯一标识查询消息详情。 |

### 请求参数说明

| 参数名 | 必填 | 参数类型 | 请求位置 | 说明 |
|---|---|---|---|---|
| messageId | 是 | String | Query | 消息唯一标识 |

### 响应参数说明

```json
{
  "code": "0",
  "message": null,
  "data": {
    "id": 1,
    "messageId": "c9de8e0da2494c3ca502d72b8ce906cf",
    "conversationId": "f339f2c68d494601a7efd4f037cd7231user_001",
    "type": "USER",
    "content": "如何创建一次安全评测任务？",
    "transformContent": null,
    "tokenCount": null,
    "modelName": null,
    "ragReferences": null,
    "metadata": null,
    "createTime": "2026-07-03 15:30:00",
    "updateTime": "2026-07-03 15:30:00",
    "deleted": false
  },
  "requestId": null
}
```

响应字段同“获取会话消息列表”的单条消息对象。未查询到时，`data` 通常为 `null`。

## 9. 保存用户消息

### 接口基本信息

| 项目 | 内容 |
|---|---|
| 接口名称 | 保存用户消息 |
| 请求路径 | `/agent/chat/message/saveUser` |
| 请求方式 | POST |
| 接口描述 | 在指定会话下保存一条用户消息，返回消息唯一标识。该接口只保存消息，不触发 AI 回复。 |

### 请求参数说明

| 参数名 | 必填 | 参数类型 | 请求位置 | 说明 |
|---|---|---|---|---|
| conversationId | 是 | String | Query | 会话唯一标识 |
| content | 是 | String | Query | 用户消息内容 |

### 响应参数说明

```json
{
  "code": "0",
  "message": null,
  "data": "c9de8e0da2494c3ca502d72b8ce906cf",
  "requestId": null
}
```

| 字段名 | 数据类型 | 说明 |
|---|---|---|
| data | String | 新保存的消息唯一标识 |

## 10. 获取最近消息

### 接口基本信息

| 项目 | 内容 |
|---|---|
| 接口名称 | 获取最近消息 |
| 请求路径 | `/agent/chat/message/recent` |
| 请求方式 | GET |
| 接口描述 | 查询指定会话最近的消息。当前实现会先取最新 `limit + 2` 条，再排除最新 2 条消息，最后按时间正序返回，适用于流式对话中排除当前轮次刚保存的用户消息和空助手消息。 |

### 请求参数说明

| 参数名 | 必填 | 参数类型 | 请求位置 | 说明 |
|---|---|---|---|---|
| conversationId | 是 | String | Query | 会话唯一标识 |
| limit | 否 | Integer | Query | 消息数量，默认 `10` |

### 响应参数说明

```json
{
  "code": "0",
  "message": null,
  "data": [
    {
      "id": 1,
      "messageId": "c9de8e0da2494c3ca502d72b8ce906cf",
      "conversationId": "f339f2c68d494601a7efd4f037cd7231user_001",
      "type": "USER",
      "content": "如何创建一次安全评测任务？",
      "transformContent": null,
      "tokenCount": null,
      "modelName": null,
      "ragReferences": null,
      "metadata": null,
      "createTime": "2026-07-03 15:30:00",
      "updateTime": "2026-07-03 15:30:00",
      "deleted": false
    }
  ],
  "requestId": null
}
```

响应字段同“获取会话消息列表”。

## 11. 删除会话下所有消息

### 接口基本信息

| 项目 | 内容 |
|---|---|
| 接口名称 | 删除会话下所有消息 |
| 请求路径 | `/agent/chat/message/deleteByConversation` |
| 请求方式 | DELETE |
| 接口描述 | 根据会话唯一标识删除该会话下的全部消息。实体配置了逻辑删除字段，实际删除行为取决于 MyBatis-Plus 逻辑删除配置。 |

### 请求参数说明

| 参数名 | 必填 | 参数类型 | 请求位置 | 说明 |
|---|---|---|---|---|
| conversationId | 是 | String | Query | 会话唯一标识 |

### 响应参数说明

```json
{
  "code": "0",
  "message": null,
  "data": true,
  "requestId": null
}
```

| 字段名 | 数据类型 | 说明 |
|---|---|---|
| data | Boolean | 是否删除成功 |

## 前端对接注意事项

1. `/agent/chat/send` 是 SSE 文本流，不要按 `Result<T>` 解包；前端需要识别 `[PROGRESS]:` 和 `[DONE]:` 前缀。
2. `[DONE]:{conversationId}` 是获取新建会话 ID 的关键来源；当请求未传 `conversationId` 时，前端应在流结束后保存该 ID。
3. `/agent/chat/send` 当前没有 Bean Validation。前端应保证 `content` 非空，并在用户未选择会话时允许 `conversationId` 为空。
4. 会话列表接口只按登录用户过滤；会话详情、归档、删除、消息查询等接口当前 Controller 层未显式校验会话归属，联调时建议确认权限策略。
5. 删除会话接口只更新会话状态，不会自动删除消息；如需同步清空消息，需要另行调用 `/agent/chat/message/deleteByConversation`。
6. `ChatConversationStatus` 枚举定义为 `ACTIVE/ARCHIVED/DELETED`，但当前归档和删除实现写入的是字符串 `archived/deleted`；如果读取归档或删除会话时出现枚举转换问题，需要后端统一状态值大小写。
7. `/agent/chat/message/recent` 会排除最新两条消息，它更适合内部构造上下文，不适合作为普通“最近 N 条消息”展示接口。
