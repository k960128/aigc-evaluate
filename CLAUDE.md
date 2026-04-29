# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

This is a Maven multi-module project. The `eval` module is the backend; the `front` module is placeholder (empty).

```bash
# Build entire project
mvn clean package

# Build eval module only
mvn clean package -pl eval

# Run the application (Spring Boot)
mvn spring-boot:run -pl eval

# Run tests
mvn test -pl eval

# Run a single test class
mvn test -pl eval -Dtest=ModelClientStrategyTest

# Run a single test method
mvn test -pl eval -Dtest=ModelClientStrategyTest#test
```

## Architecture

**Tech stack:** Spring Boot 3 + MyBatis-Plus + PostgreSQL + Lombok + Hutool

**Base package:** `com.kant.llm.eval`

### Layered structure (eval module)

```
controller/    → REST API endpoints
service/       → Business logic interfaces
service/impl/  → Business logic implementations
dao/entity/    → MyBatis-Plus DO (Data Object) entities, table name via @TableName
dao/mapper/    → MyBatis-Plus mapper interfaces (extends BaseMapper)
dto/req/       → Request DTOs
dto/resp/      → Response VOs
client/        → LLM API client abstraction layer
  strategy/    → Per-vendor LLM call strategies
engine/        → Evaluation engines (e.g., L1 interception)
scheduler/     → Scheduled tasks
common/
  config/      → Spring configurations (DB, Web)
  convention/  → Unified response wrapper (Result<T>)
  database/    → MyBatis-Plus auto-fill handler (MyMetaObjectHandler)
  enums/       → Business enums (TaskStatusEnums, ModelManufacturerEnum)
  errorcode/   → Error code definitions
  exception/   → Exception hierarchy (ClientException, ServiceException, RemoteException)
  web/         → Global exception handler, SSE emitter wrapper, Results utility
```

### Key patterns

- **API response wrapping:** All API responses are wrapped in `Result<T>` via `Results` utility class. The `GlobalExceptionHandler` converts exceptions into `Result` as well.
- **Model client strategy pattern:** LLM API calls use a strategy pattern. `ModelClientStrategy` is the interface; vendor-specific implementations live in `client/strategy/` (DeepSeek, OpenAI, Qwen, Spark). `ModelClientStrategyFactory` resolves the correct strategy from a `ModelInfo` object.
- **SSE for streaming:** `SseEmitterSender` wraps Spring's `SseEmitter` with thread-safe lifecycle management and is used for real-time evaluation progress pushing.
- **ID generation:** Snowflake IDs via `IdUtil.getSnowflake()` (Hutool).
- **MyBatis-Plus auto-fill:** `MyMetaObjectHandler` automatically populates `createTime`/`updateTime` fields.
- **Database:** PostgreSQL with MyBatis-Plus pagination interceptor configured for `DbType.POSTGRE_SQL`.

### Domain model

- **EvalTask** → evaluation task (links a model + dataset)
- **EvalTaskDetail** → task execution detail with status tracking (TaskStatusEnums: CREATING, etc.)
- **DataSet / DataSetSample** → evaluation datasets and their sample items
- **ModelInfo** → LLM model configuration stored in DB
- **EvalInterceptionTag** → L1 interception evaluation tags
- **EvalL1InterceptionSamples** → L1 interception evaluation sample records

The `L1InterceptionEngine` handles L1-level interception evaluation logic.
