# Project Overview

## 项目定位

`aigc-evaluate` 面向大模型安全评测场景，目标是提供一套后端平台能力：

- 管理被测模型及厂商配置。
- 管理评测数据集和样本。
- 创建、提交、停止和查询评测任务。
- 将任务拆分为样本级评测结果。
- 异步调用被测大模型。
- 对模型输出进行 L1 字面量风险词拦截。
- 记录样本执行结果和流水线节点日志。

换句话说，它不是单条 Prompt 测试工具，而是一个支持批量评测和结果沉淀的后端服务。

## 当前实现范围

当前仓库实现重点集中在后端：

| 能力 | 当前状态 |
| --- | --- |
| Spring Boot 后端应用 | 已实现 |
| 模型厂商和模型配置 CRUD | 已实现 |
| 模型连通性测试 | 已实现入口，依赖具体策略 |
| 数据集基础信息和样本查询 | 已实现 |
| 评测任务分页、提交、停止、状态查询 | 已实现主要接口 |
| 评测任务创建 | Controller 存在，但 Service 当前只记录日志，未真正落库 |
| RocketMQ 任务拆分 | 已实现 |
| RocketMQ 单样本执行 | 已实现 |
| 模型调用策略 | OpenAI、DeepSeek、Qwen、GLM、GPT 等有策略类；Spark 未完成实际调用 |
| L1 风险词拦截 | 已实现 AC 自动机、Redis 快照和 DB 兜底加载 |
| L2 ES/Milvus 召回 | 仅有规划/枚举，当前链路未实现 |
| L3 Judge LLM 裁决 | 仅有规划/枚举，当前链路未实现 |
| 前端项目 | 当前仓库未发现 `front` 模块 |

## 技术栈

| 类型 | 技术 |
| --- | --- |
| 语言与运行时 | Java 21 |
| Web 框架 | Spring Boot 3.5.9 |
| ORM | MyBatis-Plus 3.5.15 |
| 数据库 | MySQL |
| 缓存和分布式锁 | Redis、Redisson |
| 消息队列 | RocketMQ Spring Boot Starter |
| 模型调用 | Spring AI、DashScope、OpenAI/DeepSeek/ZhipuAI 相关集成 |
| 风险词匹配 | Aho-Corasick Double Array Trie、HanLP 依赖 |
| 认证 | Sa-Token |
| 工具库 | Lombok、Hutool、Fastjson2、Jackson、Apache Commons |

## 应用入口

应用入口是 `eval/src/main/java/com/kant/llm/LLMEvalApplication.java`。

关键注解：

- `@SpringBootApplication`：Spring Boot 应用启动入口。
- `@EnableScheduling`：开启定时任务能力。
- `@MapperScan("com.kant.llm.*.dao.mapper")`：扫描 MyBatis Mapper。

## 默认运行配置

配置文件位于 `eval/src/main/resources/application.yml`。

| 配置项 | 当前值 |
| --- | --- |
| 服务端口 | `8800` |
| Context Path | `/api/aigc-eval` |
| 应用名 | `aigc-eval` |
| 数据库 | MySQL |
| Redis | 已配置 |
| RocketMQ | 已配置 name-server 和 producer group |
| Sa-Token | token 名称为 `satoken` |

注意：当前配置文件包含数据库、Redis、RocketMQ 的实际连接信息。生产和协作环境建议迁移到环境变量、配置中心或未提交的本地配置文件中。
