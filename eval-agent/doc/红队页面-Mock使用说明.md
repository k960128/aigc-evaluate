# 产品原型与 Mock 使用说明

> 当前业务版本：2.0  
> 对齐日期：2026-09-23
> 文档职责：说明当前原型和模拟数据，正式业务要求以[产品与开发方案](红队自动化评测智能体-产品与开发方案.md)为准  
> 阅读入口：[文档导航](README.md)

## 1. 文件与打开方式

- [产品原型](red-team-agent-frontend-design.html)：HTML、样式和页面交互，覆盖当前八个业务入口。
- [Mock 数据与 API](red-team-agent-v2.mock.js)：初始数据、本地持久化、模拟状态变化和业务校验。
- [产品设计基线](红队自动化评测智能体-产品与开发方案.md)：业务含义及后端目标能力。

将 HTML 与 Mock JS 放在同一目录，直接用浏览器打开 HTML。也可以用任意静态服务器提供这两个文件。没有 CDN、字体下载或第三方网络请求，不需要安装 npm 依赖。

所有目标、人物、订单、对话、风险、费用都是虚构。页面不会调用真实模型，不会对外部应用发起攻击。后台能力均由本地 Mock 模拟，不能作为评测效果或生产工程能力的证明。

页面操作保存在当前浏览器的专用 localStorage 中，键为 redteam-product-v2-demo-20260922。文件打开与 HTTP 打开的存储相互独立。存储不可用时可以继续演示，但应导出 JSON 保存状态。

## 2. 页面入口与可操作范围

| 页面 | 可以演示的操作 |
| --- | --- |
| 评测工作台 | 三类任务入口、动态统计、最近任务、待办跳转 |
| 自动红队 | 创建计划、授权、启动、暂停、恢复、停止、会话切换、查看证据、未知结果核对 |
| 标准评测 | 选择固定用例集、查看逐条执行结果、独立展示风险 / 待定 / 执行错误 |
| 样本工厂 | 种子与变异配置、定时任务、逐条生成、修改后重新质检、审核与批量通过 |
| 风险发现 | 原始判定与独立意见、分配负责人、独立复现、生成候选用例、回归版本对比、确认修复、接受风险、重新打开 |
| 用例与策略资产 | 正式用例契约、启停、候选审核、策略修订、经验审核与来源 |
| 目标与规则 | 能力声明、登记新目标快照、规则定义、判据与版本修订 |
| 报告与待办 | 阶段报告、确认快照、导出 Markdown、统一处理授权、核对、复核、资产审核和结果跟进事项 |

所有操作以任务、风险发现和用例资产为中心，前端写操作统一经过 Mock request 方法。

自然语言辅助只使用本地关键词规则填入可编辑草案，明确标注为演示；没有调用大模型或自动执行草案。

原型用一个 tasks 对象承载一次演示运行，固定回归通过新任务演示。正式业务需要区分任务、计划版本与每次 runId；原型对象不是生产表结构。

## 3. 推荐演示路径

### 3.1 自动红队与异常控制

1. 在工作台打开“客服助手 · 隐私边界探索”。
2. 查看左侧会话、中间输入与响应、右侧判定与关联发现。
3. 在“演示控制”选择下一次结果，点击“推进一轮”。
4. 分别演示正常观察、风险命中、结果未知和 Judge 失败。
5. 结果未知时任务进入 BLOCKED，不能继续推进；填写核对依据后关闭受影响会话，允许新的尝试。
6. 暂停与停止会先显示 PAUSING / STOPPING，再进入稳定状态。

“推进一轮”是原型专用控制，不是正式产品需要用户手动执行的调度方式。时间延迟用于观察异步状态，不代表真实模型延迟。

### 3.2 风险发现到固定回归

1. 打开“跨租户测试记录出现在客服响应中”。
2. 阅读证据，执行独立复核，填写依据并确认。
3. 申请复现：消耗原授权的剩余目标、模型、Token 和费用预算；结果追加保存。
4. 生成候选回归用例，在“用例与策略资产 → 候选审核”审核通过。
5. 回到发现，选择“发起固定回归”，选择客服助手 v2.6.1-rc。
6. 批准新计划并启动，在回归任务的“下一次观察”中选择“未观察到风险”，执行全部固定用例。
7. 回到发现，查看当前指定回归，填写负责人及依据后确认修复。
8. 可以重新打开已修复问题，再创建新回归并选择“观察到风险”，演示自动重新打开；旧通过记录不能支持关闭新问题。

“接受风险”要求负责人及业务依据，不能在仍有未结束回归时直接接受；它不是技术修复。已接受风险可以人工重新打开，重新打开后需要新的回归依据。

候选未批准时不能发起固定回归；原始判定不会被复核覆盖；复现结果不会自动确认发现；回归完成不会自动关闭问题。

标准评测创建的风险发现保存具体用例与结果引用；生成候选时直接继承原任务冻结的条件、步骤和断言。历史数据没有这些引用时会明确拒绝，不会生成看似完整的通用占位用例。

### 3.3 夜间样本生产

1. 新建样本生产任务，填写至少 10 字种子、候选数量和可选计划时间。
2. 配置预算，创建并批准计划，然后启动。
3. 未来时间的任务进入排队；用“触发计划”演示定时到达。
4. 每次“生成下一个候选”产生一条候选，达到数量或预算后结束。
5. 审核、退回、拒绝或修改候选；通过后才成为正式资产。

整个生产任务的目标调用为零，生成与质检请求仍计入模型预算。

### 3.4 能力约束和版本快照

- 新建红队任务选择 DeepSeek R1 · 8B，多轮模式被禁用。
- 目标没有工具或检索轨迹时，依赖行为证据的风险选项不可选。
- 已创建的任务固定目标、规则、策略和用例快照，后续资产编辑不改变其范围。
- 确认报告时保存目标、身份、规则、策略、预算、发现、会话、证据、结果和回归上下文快照，后续任务消耗或风险复核不改变已确认报告；缺少历史快照的已确认报告不使用当前数据补写。
- 修改目标时生成新快照；修改规则或策略需要新版本号。
- 修改候选需填写依据；在候选详情“历史留痕”中展开查看当时内容、意见与状态。仅存于旧数据中、此前已经被覆盖的历史无法恢复。

### 3.5 待办与不确定结果

“报告与待办 → 我的待办”汇总计划授权、结果未知核对、风险复核、候选审核与修改、经验审核、判定跟进和报告确认。Judge 失败和调用失败可记录“仍需跟进”或“已处理本待办”；后者只从待办移除，不改原始判定，也不能让失败回归变成修复通过。

### 3.6 多风险与模式差异

红队按尝试数较少的风险优先轮换；样本工厂按风险顺序轮换。如果尝试数、候选数量或预算不足以覆盖全部风险，报告明确列出剩余项。

静态单轮使用与结果无关的预设表达；自适应单轮参考同一风险的历史尝试，但每次目标上下文独立；多轮只延续当前会话，不把其他会话反馈冒充当前上下文。所有文本仍为确定性的演示内容，不代表真实模型攻击效果。

## 4. Mock 场景与数据管理

右上角“演示数据”支持导出当前 JSON、粘贴或上传 JSON 导入、恢复初始数据。

编辑 red-team-agent-v2.mock.js 中 buildSeed() 的数据后，需要重置演示数据，已有 localStorage 不会被自动覆盖。重置前可导出备份；只操作本原型的专用存储，不清空其他页面数据。

导入必须保留 schemaVersion = 2 及所有顶层集合。导入检查重复 ID、主要引用、状态和预算；校验失败不替换原数据。各实体的业务字段仍应按本字典填写，避免省略渲染所需的展示字段。

### 4.1 顶层字段

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| schemaVersion | Number | 当前数据结构版本，固定为 2 |
| targets | Array | 被测模型或应用的目标快照 |
| rules | Array | 风险分类、定义和成功判据 |
| strategies | Array | 攻击执行策略及支持模式 |
| caseSets | Array | 正式用例集及其版本 |
| cases | Array | 已进入资产库的固定用例 |
| tasks | Array | 三类任务、运行状态和预算 |
| sessions | Array | 红队任务的会话与探测轨迹 |
| findings | Array | 风险发现、复核、整改与复现 |
| candidates | Array | 样本工厂或发现产生的候选用例 |
| results | Array | 固定用例的执行结果 |
| reports | Array | 阶段报告与已确认报告快照 |
| experiences | Array | 候选或审核后的共享经验 |
| events | Array | 任务的业务事件 |
| mock | Object | 演示延迟 latency（毫秒）及 nextOutcome（下一次模拟观察） |

ID 是字符串，必须在各自集合中唯一。引用字段填写对应集合的真实 ID。时间用于展示或计划触发，默认采用当前浏览器时区。

### 4.2 targets：目标快照

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| id / name / version | String | 快照标识、目标名称、目标版本 |
| type | Enum | MODEL、CHAT_APPLICATION、RAG_APPLICATION、TOOL_AGENT |
| environment / identity | String | 测试环境与测试身份描述，不保存凭据 |
| provider | String | 接入方式说明；本原型不会连接该服务 |
| multiTurn / reset / branch / trace | Boolean | 是否支持多轮、重置、分支和行为证据 |
| status | String | 示例为 AVAILABLE，表示可用于演示 |
| promptVersion / knowledgeVersion | String | 系统提示与知识库版本，未知时明确写未提供 |
| note | String | 目标能力与复现限制说明 |

### 4.3 rules、strategies、caseSets

| 对象 / 字段 | 类型 | 含义 |
| --- | --- | --- |
| Rule.id / name / version | String | 风险编码、名称、规则版本 |
| Rule.severity | Enum | HIGH、MEDIUM 等默认风险等级 |
| Rule.needsTrace | Boolean | 判定此类风险是否需要实际工具或检索证据 |
| Rule.definition / criterion / evidence | String | 业务定义、成功判据、所需证据说明 |
| Rule.active | Boolean | 是否允许新任务选用 |
| Rule.history | Array，可选 | 修订前版本的定义和判据 |
| Strategy.id / name / version | String | 策略标识、名称与版本 |
| Strategy.mode | Enum | STATIC_SINGLE_TURN、ADAPTIVE_SINGLE_TURN、ADAPTIVE_MULTI_TURN |
| Strategy.description | String | 如何组织攻击与反馈 |
| Strategy.maxAttempts | Number | 单任务最大尝试数 |
| Strategy.active | Boolean | 是否可被新计划选用 |
| Strategy.history | Array，可选 | 策略修订前的配置 |
| CaseSet.id / name / version / description | String | 用例集标识、名称、版本与说明 |

### 4.4 tasks：任务

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| id / name | String | 任务标识与名称 |
| type | Enum | RED_TEAM_EXPLORATION、STANDARD_EVALUATION、SAMPLE_PRODUCTION |
| status | Enum | 本地支持 DRAFT、WAITING_APPROVAL、READY、QUEUED、RUNNING、PAUSING、PAUSED、STOPPING、STOPPED、BLOCKED、COMPLETED、CANCELLED、FAILED；分别表示草案、待授权、待启动、排队、运行、正在暂停、已暂停、正在停止、已停止、待核对、执行结束、已取消、失败 |
| targetId / targetVersion | String | 目标引用与版本；样本生产 targetId 为空 |
| risks | Array<String> | 允许测试或生产的风险编码 |
| mode / strategyId | String | 红队执行模式与策略引用 |
| purpose | Enum，可选 | BASELINE 基准或 REGRESSION 修复回归 |
| setId / setVersion | String，可选 | 标准评测绑定的用例集与版本 |
| caseIds / caseSnapshots | Array，可选 | 固定的用例 ID 和内容快照 |
| findingId | String，可选 | 修复回归对应的风险发现 |
| baseline | Array，可选 | 每个回归用例的对照记录，标明是同版本固定用例执行还是原发现观察，不冒充同条件对比 |
| seed / mutations | String，可选 | 样本生产种子与变异要求 |
| quantity | Number，可选 | 计划生成的候选数量 |
| schedule | String，可选 | 本地时间格式的计划时间；空值代表手动启动 |
| createdAt / owner | String | 创建时间与发起人 |
| startedAt | Number，可选 | 启动时间的毫秒时间戳，用于演示运行期限 |
| version / planVersion | Number / String | 任务修订序号与计划版本 |
| authorization | String | 本次批准范围的可读摘要 |
| stopReason | String | 停止、阻塞或结束的具体原因 |
| coverage | Object | executed 为已记录覆盖项数，planned 为计划项数 |
| budget | Object | 下表中的限制和已消耗账目 |
| targetSnapshot / ruleSnapshots / strategySnapshot | Object / Array / Object | 创建任务时固定的目标、规则和策略 |
| nextOutcome | String，可选 | 初始场景的提示值；实际下一轮由演示控制或请求参数决定 |

### 4.5 budget：预算与消耗

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| maxTargetCalls / targetCalls | Number | 目标请求上限 / 已用次数，包含独立复现 |
| maxAgentCalls / agentCalls | Number | 生成、攻击、判定等模型请求上限 / 已用次数 |
| maxTokens / tokens | Number | Token 总上限 / 已用数量 |
| maxCost / cost | Number | 费用上限 / 已用金额，演示统一为人民币 |
| maxTurns | Number | 多轮会话的最大输入轮数 |
| maxAttempts | Number | 最大攻击尝试数 |
| maxDuration | Number | 最长运行秒数 |
| maxConcurrent | Number | 计划中的并发会话上限；本原型手动逐次推进，不模拟真实并发工作者 |

“推进一轮”的预算计量为固定演示值，实际后端需要真实用量、预留与结算机制。原型不实现分布式调度、真实模型计费或外部系统恰好一次执行保证。

原型创建计划直接进入 WAITING_APPROVAL，省略真实模型规划的 PLANNING；即时启动、恢复和核对后可直接进入 RUNNING。正式排队与恢复语义以产品方案第 10 节为准。

### 4.6 sessions 与 probes：会话和单次探测

| 对象 / 字段 | 类型 | 含义 |
| --- | --- | --- |
| Session.id / taskId / name | String | 会话标识、所属任务、显示名称 |
| Session.risk | String | 当前风险编码 |
| Session.status | Enum | ACTIVE、BLOCKED、CLOSED |
| Session.parent | String | 父分支引用，空值表示根会话 |
| Session.resolution | String，可选 | 对未知执行结果的人工核对说明 |
| Session.probes | Array | 按会话顺序保存的探测 |
| Probe.id / input / output | String | 探测标识、输入、原始响应或错误信息 |
| Probe.verdict | Enum | RISK_OBSERVED、NO_RISK_OBSERVED、INCONCLUSIVE |
| Probe.executionStatus | Enum | SUCCEEDED、UNKNOWN 等真实执行状态的模拟值 |
| Probe.reason | String | 简短观察与下一步依据，不是内部思维链 |
| Probe.evidence | String | 可读执行证据，本原型以纯文本安全展示 |
| Probe.createdAt / tokens | String / Number | 时间与模拟 Token 用量 |

### 4.7 findings：风险发现

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| id / title / risk / severity | String | 发现标识、标题、风险编码、等级 |
| status | Enum | SUSPECTED、CONFIRMED、DISMISSED、INCONCLUSIVE |
| remediation | Enum | OPEN、IN_PROGRESS、READY_FOR_RETEST、RESOLVED、REOPENED、ACCEPTED_RISK；接受风险不计为技术修复 |
| taskId / targetId / sessionId / probeId | String | 原任务、目标、会话、探测引用；固定用例发现可没有会话引用 |
| owner / firstSeen | String | 整改负责人、首次观察时间 |
| originalVerdict | String | 原始判定，复核不修改它 |
| review / note | String | 最新独立意见与补充说明，历史意见见 history |
| reproductions | Array | 独立复现记录 |
| history | Array | 处理历史；每项包含 time（时间）、text（动作或意见） |
| retestTaskId / repairTargetId | String，可选 | 当前指定的修复回归任务和修复目标快照；确认修复只使用这两个引用 |
| resolution / acceptance | Object，可选 | 修复确认或接受风险的负责人、依据、时间和关联回归；两者含义不同 |

每条复现记录包含 id（复现标识）、status（RUNNING / COMPLETED / INTERRUPTED）、verdict（观察结论）、time（时间）、targetCalls（该次目标调用数量）。刷新时未完成的模拟复现会标为中断和不确定，不冒充完成。

### 4.8 candidates 与 cases：候选与正式用例

| 对象 / 字段 | 类型 | 含义 |
| --- | --- | --- |
| Candidate.id / taskId / title / risk | String | 候选标识、所属任务、名称、风险编码 |
| Candidate.content | String | 候选文本或完整固定会话步骤 |
| Candidate.revision | Number | 当前候选修订号；修改后递增 |
| Candidate.quality | Enum | PASS、REVISE、REJECT |
| Candidate.status | Enum | PENDING、REVISION、APPROVED、REJECTED |
| Candidate.lineage / feedback | String | 来源与变异链、质量或人工意见 |
| Candidate.sourceFinding | String | 来源发现；纯样本生产为空 |
| Candidate.caseType / preconditions / assertion | String，可选 | 从发现沉淀时保留的用例类型、条件和断言 |
| Candidate.ruleVersion / ruleSnapshot / assertionRef | String / Object | 候选生成时绑定的规则版本、规则快照和断言引用；不随目录新版本变化 |
| Candidate.sourceCaseId / sourceCaseVersion / sourceTargetSnapshot | String / Object，可选 | 标准评测发现对应的原始用例及目标快照 |
| Candidate.evidenceRefs / sourceEvidence | Array | 沉淀候选引用的探测或结果 ID，以及只读证据快照 |
| Candidate.history | Array | 候选每次生成、退回、编辑、审核前后的追加留痕；包含旧内容和旧意见 |
| Case.id / name / version / setId | String | 正式用例标识、名称、版本、所属用例集 |
| Case.risk / type | String | 风险编码及 SINGLE_MESSAGE / CONVERSATION / SCENARIO |
| Case.preconditions / steps / assertion | String | 前置条件、固定步骤、判断是否命中的规则 |
| Case.assertionRef / ruleSnapshot | String / Object | 正式用例绑定的规则版本和判据快照 |
| Case.sourceFinding / status | String | 来源发现；ACTIVE 或 DISABLED |
| Case.sourceCandidateId / sourceCandidateRevision | String / Number，可选 | 正式用例来源的候选标识和审核时修订号 |

Candidate.history 每项包含 time（时间）、actor（演示操作者）、action（动作编码）、note（依据）、revision（修订号）、content（当时内容）、feedback（当时审核意见）、quality（当时质检状态）、status（当时审核状态）。历史以追加方式保存；旧数据缺失的历史不补造。

Task.baseline 每项包含 caseId / version（本次用例和版本）、targetVersion（对照目标版本）、verdict（原观察结论）、evidenceRef（原证据引用）、kind / label（对照类型及中文说明）。SOURCE_OBSERVATION 表示原发现观察，不是同版本用例重跑；SAME_CASE 表示步骤、断言和规则引用一致的固定用例记录。

### 4.9 results、reports、experiences、events

| 对象 / 字段 | 类型 | 含义 |
| --- | --- | --- |
| Result.id / taskId / caseId / caseName | String | 结果标识、任务、用例及名称 |
| Result.verdict / response | String | 判定与响应摘要；INCONCLUSIVE 和 EXECUTION_ERROR 不计为无风险 |
| Result.executionStatus / caseVersion / ruleVersion | String | 真实执行状态模拟值、执行用例版本、使用的判定规则版本 |
| Result.followup / followupHistory | Object / Array，可选 | 对无法判断或执行错误的跟进动作与依据；不覆盖原始判定 |
| Report.id / taskId / title / createdAt | String | 报告标识、任务、标题、生成时间 |
| Report.status / confirmedAt | String | DRAFT 或 PUBLISHED，以及可选确认时间 |
| Report.snapshot | Object，可选 | 确认时固定 task、findings、targets、rules、results、sessions、sourceResults、candidates、cases、regressionTasks 和 capturedAt；没有快照的历史确认报告禁止用实时数据重建 |
| Experience.id / title / status | String | 经验标识、标题、PENDING / APPROVED / REJECTED |
| Experience.findingId / scope / evidence / text | String | 来源发现、适用范围、证据、经验摘要 |
| Event.id / taskId / time / type / text | String | 唯一事件、关联任务、时间、事件类型与说明 |

## 5. Mock API 与后端接入边界

window.RedTeamMock 暴露四个主要能力：

| 方法 | 用途 |
| --- | --- |
| getState() | 返回完整数据副本，修改副本不会修改原型状态 |
| request(action, payload) | 异步模拟命令，校验后持久化并发出 redteam:change 事件 |
| buildSeed() | 返回初始虚构场景 |
| validateImport(data) | 检查导入结构和主要关联，成功时返回副本 |

主要 action 如下。所有参数字段在上面的实体字典或下表说明；它们是本地模拟命令，不是已实现后端路由。

| action | payload | 行为 |
| --- | --- | --- |
| createTask | type、name、targetId、risks、mode、strategyId、预算配置；按任务类型追加种子或用例集字段 | 创建待授权计划，返回任务 ID |
| approve / rejectPlan / start | id：任务 ID | 审批、拒绝、启动 |
| command | id：任务 ID；command：pause / resume / stop | 模拟异步运行控制 |
| tick | id：任务 ID；outcome：safe / risk / unknown / judgeError / executionError | 推进一次演示执行；红队和标准评测均支持五种结果，工厂忽略 outcome 并新增待审核候选 |
| triggerSchedule | id：排队任务 ID | 手动演示定时触发 |
| resolveUnknown | id：任务 ID；note：核对依据 | 保留原未知记录，关闭会话，允许新尝试 |
| reviewSample | id：候选 ID；decision：approve / revise / reject / edit；编辑需 content 与 note，其余退回或拒绝需 note | 审核或修订并追加历史，批准后创建正式用例 |
| reviewFinding | id：发现 ID；status：确认状态；note：依据 | 保存独立复核意见 |
| assign | id：发现 ID；owner：负责人 | 分配已确认问题 |
| replay | id：发现 ID；可选 outcome=safe | 新建独立复现，扣除预算，不自动确认发现 |
| makeCandidate | id：发现 ID | 从确认发现生成待审核回归用例 |
| regression | id：发现 ID；targetId：修复快照 | 生成待批准的固定用例回归计划 |
| resolveFinding | id、regressionTaskId、owner、note | 仅依据当前指定回归、完整无风险结果和负责人依据确认修复；历史通过记录不能替代 |
| reopenFinding | id、owner、note | 使已修复或已接受风险的问题重新进入整改，并清空当前回归引用 |
| acceptRisk | id、owner、note | 记录负责人接受风险；不计为技术修复 |
| recordFollowup | id、status、note | 为待定或执行错误记录后续处理，不覆盖原判定 |
| report / publishReport | id：任务 ID / 报告 ID | 生成草稿 / 确认不可变快照 |
| saveTarget / saveRule / saveStrategy | 相应实体的新版本字段 | 保存修订，保留旧任务快照 |
| toggleCase | id：正式用例 ID | 切换后续选用状态 |
| reviewExperience | id：经验 ID；approve：Boolean | 审核经验，来源未确认时禁止批准 |
| mockOutcome | outcome：五种模拟观察之一 | 设置页面下一次演示观察；标准评测 API 不传时默认 safe |
| import / reset | data：完整数据 / 无参数 | 校验导入 / 恢复初始数据 |

createTask 预算输入使用 maxTargetCalls、maxAgentCalls、maxTokens、maxCost、maxTurns、maxDuration，单位与第 4.5 节一致；工厂额外需要 seed、quantity，可提供 mutations 和 schedule；标准评测需要 setId，可提供 purpose。

原型中的多风险任务会轮换实际风险，会区分静态单轮、自适应单轮和自适应多轮；标准评测和红队都支持风险、待定、未知和调用失败的不同记录。真正接入后端时，替换读取与命令适配层，并按产品方案加入身份、授权、不可变版本、幂等键、分页和可靠事件。不能把 localStorage、固定延迟或演示数据校验当作生产权限与任务执行实现。

## 6. 当前演示边界与兼容

- 每个红队任务仅绑定一个策略，使用线性会话；不提供跨策略自主规划或状态分支。
- 任务与运行尚未分表，真实预授权策略、计划修订重审批、SSE、分布式预算、发现归并拆分、CI、真实样本去重质检和长期经验检索未实现；正式设计仍以产品方案为准。
- 自动红队与标准评测的“执行错误”表示目标调用失败；Judge 失败保留执行事实，判定为无法判断。会话摘要不把待定或错误显示为无风险。
- 覆盖记录只统计有明确判定的风险；工厂统计已生成候选的风险。正式证据完整性与风险 × 策略指标不由本地 Mock 证明。
- 报告页面与 Markdown 来自同一正文，包含规则、授权、模式、预算、未覆盖项、发现、原始证据、复现和回归。原始内容作为文本转义，不作为 HTML 或链接执行。
- 保留原 localStorage 键及 schemaVersion = 2，不自动清空已有演示数据。旧数据缺少证据引用、规则绑定或报告快照时，展示限制或拒绝相关动作；可先导出备份，再通过“恢复初始演示”载入完整的新场景。
- 原型新增只读辅助方法：sessionVerdict(session) 计算保守会话摘要；getCoverage(taskId) 读取已明确观察的风险；getCurrentRegression(findingId) 读取当前回归；getRegressionComparison(taskId) 读取固定用例对照；getReportSnapshot(taskId) 构建报告草稿的事实副本。

验证命令：`node --test eval-agent/prototype-tests/red-team-prototype.test.cjs`，在仓库根目录执行，仅操作内存数据。
