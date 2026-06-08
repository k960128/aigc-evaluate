# AIGC Evaluate Code Wiki

本目录是 `aigc-evaluate` 项目的结构化 Code Wiki。内容基于当前仓库源码整理，重点说明项目架构、模块职责、关键类、核心流程、依赖关系和运行方式。

## 项目一句话定位

`aigc-evaluate` 是一个大模型安全评测平台后端。它把模型配置、数据集样本、评测任务、异步执行、模型调用和 L1 风险词拦截串成一条评测链路，用于批量评估被测大模型输出是否安全。

## 阅读路线

| 文档 | 内容 |
| --- | --- |
| [01-project-overview.md](01-project-overview.md) | 项目定位、当前实现范围、技术栈 |
| [02-architecture.md](02-architecture.md) | 整体架构、分层结构、核心链路图 |
| [03-module-responsibilities.md](03-module-responsibilities.md) | 主要包与模块职责 |
| [04-domain-model.md](04-domain-model.md) | 核心领域模型、表实体和状态枚举 |
| [05-key-flows.md](05-key-flows.md) | 评测任务、MQ、模型调用、L1 拦截等关键流程 |
| [06-key-classes-and-functions.md](06-key-classes-and-functions.md) | 关键类和关键方法说明 |
| [07-api-entrypoints.md](07-api-entrypoints.md) | REST API 入口总览 |
| [08-dependencies-and-config.md](08-dependencies-and-config.md) | Maven 依赖、运行配置、外部服务关系 |
| [09-runbook.md](09-runbook.md) | 构建、测试、启动和排查指南 |
| [10-known-gaps-and-risks.md](10-known-gaps-and-risks.md) | 当前实现缺口、风险和维护建议 |

## 当前仓库结构概览

```text
aigc-evaluate/
  pom.xml                 Maven 父项目
  eval/                   Spring Boot 后端模块
    pom.xml
    src/main/java/com/kant/llm/
      LLMEvalApplication.java
      eval/
        controller/       REST API
        service/          业务服务与节点记录器
        service/impl/     业务服务实现
        dao/entity/       MyBatis-Plus DO 实体
        dao/mapper/       MyBatis-Plus Mapper
        dto/req/          请求 DTO
        dto/resp/         响应 VO
        client/           模型调用抽象与策略工厂
        client/strategy/  厂商模型调用策略
        engine/           L1 风险词拦截引擎
        mq/               RocketMQ topic、消息、生产者、消费者
        scheduler/        定时任务扩展点
        common/           配置、统一响应、异常、枚举、Web 工具
    src/main/resources/
      application.yml
```

## 重要说明

- 当前 Maven 父项目只声明了 `eval` 模块。
- 当前代码主链路已经接入 RocketMQ：任务提交后异步拆分样本，再异步执行单样本评测。
- 当前安全判定已实现 L1 风险词 AC 自动机拦截；L2 双路召回、L3 Judge LLM 在枚举和规划中存在，但当前业务链路尚未实现。
- `doc/wiki` 只描述当前源码真实状态；历史文档或规划中出现但当前代码不存在的能力，会在缺口文档中单独标注。
