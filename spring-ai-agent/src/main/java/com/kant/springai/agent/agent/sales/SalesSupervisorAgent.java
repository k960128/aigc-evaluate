package com.kant.springai.agent.agent.sales;

import com.kant.springai.agent.common.config.SalesMultiAgentModelProvider;
import com.kant.springai.agent.common.config.SalesMultiAgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 销售 Supervisor Agent Demo。
 *
 * <p>Supervisor 本身不持有业务工具，只负责根据共享执行状态选择 Worker、
 * 接收执行结果，并判断继续委派还是结束。每个 Worker 拥有独立的角色和工具边界。</p>
 */
@Slf4j
@Component
public class SalesSupervisorAgent {

    private static final String FINISH = "FINISH";
    private static final int HARD_MAX_DELEGATIONS = 10;

    private static final String SUPERVISOR_SYSTEM_PROMPT = """
            你是销售分析团队的 Supervisor Agent。

            你只负责：
            1. 根据用户目标和已有执行记录选择下一位专业 Agent；
            2. 为该 Agent 生成明确、可执行且范围单一的指令；
            3. 当证据已经足够时返回 FINISH。

            严格限制：
            - 你不能直接查询数据、计算指标或生成图表；
            - 每轮只能选择一个 Agent；
            - 只能从提供的 Agent 名称或 FINISH 中选择；
            - 不要重复已经完成且结果有效的任务；
            - 只有用户明确要求图表或可视化时才选择 chart；
            - routingReason 只写一句可审计的路由理由，不输出详细思维过程；
            - 信息足够、问题超出团队能力或无需调用 Worker 时，选择 FINISH。
            """;

    private static final String SYNTHESIZER_SYSTEM_PROMPT = """
            你是销售分析团队的 Supervisor Agent，负责汇总专业 Agent 的最终结果。

            汇总规则：
            - 只使用用户问题和 Worker 执行结果中的信息，不得编造数字；
            - 明确区分数据事实、分析判断和信息不足；
            - 合并重复内容，保留关键时间范围、筛选条件和指标；
            - 如果 Worker 结果互相冲突，要明确指出冲突；
            - 如果存在以 CHART_JSON: 开头的内容，必须逐字完整保留，不得改写、截断，
              且不能使用 Markdown 代码块包裹；
            - 使用中文给出直接、清晰的最终答复。
            """;

    private final SalesMultiAgentModelProvider modelProvider;
    private final SalesMultiAgentProperties properties;
    private final Map<String, SalesWorkerAgent> workers;
    private final BeanOutputConverter<SupervisorDecision> decisionConverter =
            new BeanOutputConverter<>(SupervisorDecision.class);

    private volatile ChatClient supervisorChatClient;

    public SalesSupervisorAgent(SalesMultiAgentModelProvider modelProvider,
                                SalesMultiAgentProperties properties,
                                List<SalesWorkerAgent> workerAgents) {
        this.modelProvider = modelProvider;
        this.properties = properties;
        Map<String, SalesWorkerAgent> registeredWorkers = workerAgents.stream()
                .sorted(Comparator.comparing(SalesWorkerAgent::name))
                .collect(Collectors.toMap(
                        worker -> normalizeAgentName(worker.name()),
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalStateException("存在重复的 Worker Agent: " + left.name());
                        },
                        LinkedHashMap::new));
        this.workers = Collections.unmodifiableMap(registeredWorkers);
        Assert.notEmpty(this.workers, "至少需要注册一个 SalesWorkerAgent");
    }

    /**
     * 执行一次完整的 Supervisor 工作流。
     *
     * @param question 用户问题
     * @param today 当前日期，格式 yyyy-MM-dd
     * @return 路由轨迹和最终回答
     */
    public SupervisorResponse chat(String question, String today) {
        Assert.hasText(question, "用户问题不能为空");
        Assert.hasText(today, "当前日期不能为空");

        int maxDelegations = Math.max(1,
                Math.min(properties.getMaxDelegations(), HARD_MAX_DELEGATIONS));
        List<ExecutionStep> steps = new ArrayList<>();
        String finishReason = null;

        for (int round = 1; round <= maxDelegations; round++) {
            SupervisorDecision decision = decide(question, today, steps);
            log.info("Supervisor 第 {} 轮决策: nextAgent={}, reason={}",
                    round, decision.nextAgent(), decision.routingReason());

            if (FINISH.equals(decision.nextAgent())) {
                finishReason = StringUtils.hasText(decision.routingReason())
                        ? decision.routingReason()
                        : "Supervisor 判断已有信息足够。";
                break;
            }

            SalesWorkerAgent worker = workers.get(decision.nextAgent());
            String history = renderHistory(steps);
            long startedAt = System.currentTimeMillis();
            String output = worker.execute(new SalesWorkerAgent.WorkerRequest(
                    question,
                    decision.instruction(),
                    today,
                    history));
            long durationMs = System.currentTimeMillis() - startedAt;

            steps.add(new ExecutionStep(
                    round,
                    worker.name(),
                    decision.routingReason(),
                    decision.instruction(),
                    output,
                    durationMs));
        }

        boolean maxDelegationsReached = finishReason == null;
        if (maxDelegationsReached) {
            finishReason = "达到最大 Agent 交接次数，Supervisor 强制进入汇总阶段。";
        }

        String answer = synthesize(question, today, steps, finishReason);
        return new SupervisorResponse(
                question,
                List.copyOf(steps),
                finishReason,
                maxDelegationsReached,
                answer);
    }

    private SupervisorDecision decide(String question,
                                      String today,
                                      List<ExecutionStep> steps) {
        String request = """
                【当前日期】
                %s

                【用户问题】
                %s

                【可用专业 Agent】
                %s

                【已经完成的执行记录】
                %s

                返回一个路由决策，字段要求：
                - nextAgent：下一位 Agent 的名称，或 FINISH；
                - instruction：交给下一位 Agent 的具体任务；选择 FINISH 时可为空字符串；
                - routingReason：一句简短、可审计的路由理由，不要输出详细思维过程。

                必须严格遵循以下结构化输出格式：
                %s
                """.formatted(
                today,
                question,
                renderWorkerCatalog(),
                renderHistory(steps),
                decisionConverter.getFormat());

        String raw = getSupervisorChatClient().prompt()
                .system(SUPERVISOR_SYSTEM_PROMPT)
                .user(request)
                .call()
                .content();

        SupervisorDecision decision;
        try {
            decision = decisionConverter.convert(raw);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Supervisor 返回了无法解析的路由结果", exception);
        }
        return validateDecision(decision);
    }

    private SupervisorDecision validateDecision(SupervisorDecision decision) {
        if (decision == null || !StringUtils.hasText(decision.nextAgent())) {
            throw new IllegalStateException("Supervisor 未返回 nextAgent");
        }

        String nextAgent = normalizeAgentName(decision.nextAgent());
        String routingReason = StringUtils.hasText(decision.routingReason())
                ? decision.routingReason().trim()
                : "根据当前任务状态进行下一步路由。";

        if (FINISH.equals(nextAgent)) {
            return new SupervisorDecision(FINISH, "", routingReason);
        }
        if (!workers.containsKey(nextAgent)) {
            throw new IllegalStateException("Supervisor 返回了未知 Agent: " + decision.nextAgent());
        }
        if (!StringUtils.hasText(decision.instruction())) {
            throw new IllegalStateException("Supervisor 没有为 " + nextAgent + " 提供执行指令");
        }

        return new SupervisorDecision(
                nextAgent,
                decision.instruction().trim(),
                routingReason);
    }

    private String synthesize(String question,
                              String today,
                              List<ExecutionStep> steps,
                              String finishReason) {
        String request = """
                【当前日期】
                %s

                【用户问题】
                %s

                【结束原因】
                %s

                【专业 Agent 执行结果】
                %s

                请生成最终回答。如果没有执行任何 Worker，说明该问题为何无需调用 Worker，
                或说明它超出了销售分析团队的能力边界。
                """.formatted(
                today,
                question,
                finishReason,
                renderHistory(steps));

        return getSupervisorChatClient().prompt()
                .system(SYNTHESIZER_SYSTEM_PROMPT)
                .user(request)
                .call()
                .content();
    }

    private String renderWorkerCatalog() {
        return workers.values().stream()
                .map(worker -> "- %s：%s".formatted(worker.name(), worker.description()))
                .collect(Collectors.joining("\n"));
    }

    private String renderHistory(List<ExecutionStep> steps) {
        if (steps.isEmpty()) {
            return "（暂无，尚未调用任何 Worker Agent）";
        }

        StringBuilder history = new StringBuilder();
        for (ExecutionStep step : steps) {
            history.append("## 第 ")
                    .append(step.round())
                    .append(" 轮：")
                    .append(step.agent())
                    .append("\n")
                    .append("Supervisor 指令：")
                    .append(step.instruction())
                    .append("\n")
                    .append("Worker 输出：\n")
                    .append(step.output())
                    .append("\n\n");
        }
        return history.toString();
    }

    private ChatClient getSupervisorChatClient() {
        ChatClient current = supervisorChatClient;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (supervisorChatClient == null) {
                supervisorChatClient = ChatClient.builder(modelProvider.getChatModel())
                        .defaultAdvisors(new SimpleLoggerAdvisor())
                        .build();
            }
            return supervisorChatClient;
        }
    }

    private static String normalizeAgentName(String agentName) {
        String normalized = agentName.trim();
        if (FINISH.equalsIgnoreCase(normalized)) {
            return FINISH;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * Supervisor 每一轮的结构化路由决策。
     */
    public record SupervisorDecision(
            String nextAgent,
            String instruction,
            String routingReason) {
    }

    /**
     * 一次 Supervisor -> Worker 交接及执行结果。
     */
    public record ExecutionStep(
            int round,
            String agent,
            String routingReason,
            String instruction,
            String output,
            long durationMs) {
    }

    /**
     * 一次完整 Supervisor 工作流的响应。
     */
    public record SupervisorResponse(
            String question,
            List<ExecutionStep> steps,
            String finishReason,
            boolean maxDelegationsReached,
            String answer) {
    }
}
