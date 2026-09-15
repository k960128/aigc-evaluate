package com.kant.springai.agent.agent;

import com.kant.springai.agent.common.config.RedisChatMemoryRepository;
import com.kant.springai.agent.controller.SqlOptimizeController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 智能优化 Agent。
 * 这个类用于演示 Evaluator-Optimizer（评估优化器模式）。
 */
@Slf4j
@Component
public class SqlOptimizeAgent {

    private final ChatClient chatClient;

    public SqlOptimizeAgent(RedisChatMemoryRepository redisChatMemoryRepository) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(10)
                .chatMemoryRepository(redisChatMemoryRepository).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey("QflVuNNAlVBZPwYKUfar:YCkTXhZTNkPfyrQJBaVf")
                        .baseUrl("https://spark-api-open.xf-yun.com/")
                        .build())
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model("Pro")
                                .build()
                )
                .build();
        this.chatClient = ChatClient
                .builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 默认最大优化轮次。
     * 注意：生产环境中一定要设置最大轮次，避免模型一直无法 PASS 导致无限循环。
     */
    public static final int DEFAULT_MAX_ROUNDS = 3;

    /**
     * Optimizer Prompt。
     * 负责生成或优化 SQL。
     * 注意：这里要求模型返回结构化 JSON，方便 Spring AI 映射成 Java Record。
     */
    private static final String OPTIMIZER_PROMPT = """
            你是一名资深数据库 SQL 优化专家。
            
            你的任务是根据用户输入的原始 SQL、表结构信息、索引信息、业务背景和历史反馈，
            生成一份优化后的 SQL 方案。
            
            你需要重点关注：
            - 是否存在 SELECT *；
            - 是否存在函数导致索引失效；
            - 是否存在隐式类型转换；
            - 是否存在不合理的 JOIN；
            - 是否存在子查询可优化空间；
            - 是否存在排序、分页、分组性能问题；
            - 是否需要补充索引；
            - 优化后 SQL 是否和原始 SQL 保持语义一致。
            
            输出要求：
            - 只输出 JSON；
            - 不要输出 Markdown 代码块；
            - optimizedSql 字段中如果有换行，请使用 \\n；
            - 不要编造不存在的表字段；
            - 如果缺少表结构或索引信息，要在 risk 字段中说明。
            
            JSON 格式如下：
            
            {
              "analysis": "对原始 SQL 问题的分析",
              "optimizedSql": "优化后的 SQL",
              "optimizationPoints": "本次优化点说明",
              "indexSuggestions": "索引建议",
              "risk": "潜在风险或需要人工确认的地方"
            }
            """;

    /**
     * Evaluator Prompt。
     * 负责评估 Optimizer 生成的 SQL 优化结果。
     */
    private static final String EVALUATOR_PROMPT = """
            你是一名资深数据库审核专家。
            
            请评估下面这份 SQL 优化结果是否合格。
            
            评估标准：
            - 优化后的 SQL 是否和原始 SQL 语义一致；
            - 是否避免了明显索引失效问题；
            - 是否减少了不必要字段返回；
            - 是否存在错误改写；
            - 是否存在分页、排序、分组、JOIN 风险；
            - 索引建议是否合理；
            - 是否有必要补充风险说明；
            - 是否可以直接交给开发同学参考。
            
            evaluation 字段只能是：
            - PASS：优化结果合格；
            - NEEDS_IMPROVEMENT：存在可改进问题；
            - FAIL：优化结果明显错误或风险较高。
            
            输出要求：
            - 只输出 JSON；
            - 不要输出 Markdown 代码块；
            - feedback 要具体、可执行。
            
            JSON 格式如下：
            
            {
              "evaluation": "PASS",
              "feedback": "评估意见"
            }
            """;

    /**
     * 执行 SQL 智能优化。
     * @param request SQL 优化请求
     * @return 最终优化结果
     */
    public RefinedResponse optimize(SqlOptimizeController.SqlOptimizeRequest request) {
        // memory 保存每一轮优化后的 SQL。
        // 下一轮优化时，会把这些历史尝试作为参考输入。
        List<String> memory = new ArrayList<>();

        // chainOfThought 保存每一轮完整的生成结果。
        // 最终返回时，可以看到每一轮是怎么优化的。
        List<Generation> chainOfThought = new ArrayList<>();

        return loop(request, "", memory, chainOfThought, 1);
    }

    /**
     * Evaluator-Optimizer 核心循环。
     * @param request        原始 SQL 优化请求
     * @param context        历史尝试和反馈上下文
     * @param memory         历史优化 SQL 记忆
     * @param chainOfThought 完整优化轨迹
     * @param round          当前轮次
     * @return 最终优化响应
     */
    private RefinedResponse loop(SqlOptimizeController.SqlOptimizeRequest request,
                                 String context,
                                 List<String> memory,
                                 List<Generation> chainOfThought,
                                 int round) {

        log.info("开始第 {} 轮 SQL 优化", round);

        // 1、Optimizer 生成或优化 SQL。
        Generation generation = generate(request, context, round);

        // 2、保存本轮生成结果。
        memory.add(generation.optimizedSql());
        chainOfThought.add(generation);

        log.info("""
                === OPTIMIZER OUTPUT ===
                ROUND: {}
                ANALYSIS:
                {}
                
                OPTIMIZED SQL:
                {}
                """, round, generation.analysis(), generation.optimizedSql());

        // 3、Evaluator 评估本轮优化结果。
        EvaluationResponse evaluationResponse = evaluate(request, generation);

        log.info("""
                === EVALUATOR OUTPUT ===
                ROUND: {}
                EVALUATION: {}
                FEEDBACK:
                {}
                """, round, evaluationResponse.evaluation(), evaluationResponse.feedback());

        // 4、如果评估通过，直接返回最终结果。
        if (evaluationResponse.evaluation() == Evaluation.PASS) {
            return new RefinedResponse(
                    generation,
                    evaluationResponse,
                    chainOfThought,
                    true,
                    round
            );
        }

        // 5、如果达到最大轮次仍未通过，也返回最后一次结果。
        int maxRounds = request.maxRounds() != null ? request.maxRounds() : DEFAULT_MAX_ROUNDS;
        if (round >= maxRounds) {
            return new RefinedResponse(
                    generation,
                    evaluationResponse,
                    chainOfThought,
                    false,
                    round
            );
        }

        // 6、评估未通过，构造下一轮优化上下文。
        String newContext = buildContext(memory, evaluationResponse);

        // 7、继续下一轮优化。
        return loop(request, newContext, memory, chainOfThought, round + 1);
    }

    /**
     * Optimizer：生成或优化 SQL。
     * @param request 原始 SQL 优化请求
     * @param context 历史尝试和反馈
     * @param round   当前轮次
     * @return 本轮生成结果
     */
    private Generation generate(SqlOptimizeController.SqlOptimizeRequest request,
                                String context,
                                int round) {
        return chatClient.prompt()
                .user(user -> user.text("""
                                {prompt}
                                
                                当前轮次：
                                {round}
                                
                                原始 SQL：
                                {sql}
                                
                                表结构信息：
                                {schema}
                                
                                已有索引信息：
                                {indexes}
                                
                                业务背景：
                                {businessContext}
                                
                                历史优化反馈：
                                {context}
                                """)
                        .param("prompt", OPTIMIZER_PROMPT)
                        .param("round", round)
                        .param("sql", request.sql())
                        .param("schema", defaultString(request.schema()))
                        .param("indexes", defaultString(request.indexes()))
                        .param("businessContext", defaultString(request.businessContext()))
                        .param("context", defaultString(context)))
                .call()
                .entity(Generation.class);
    }

    /**
     * Evaluator：评估优化结果。
     * @param request    原始 SQL 优化请求
     * @param generation Optimizer 生成结果
     * @return 评估结果
     */
    private EvaluationResponse evaluate(SqlOptimizeController.SqlOptimizeRequest request,
                                        Generation generation) {
        return chatClient.prompt()
                .user(user -> user.text("""
                                {prompt}
                                
                                原始 SQL：
                                {sql}
                                
                                表结构信息：
                                {schema}
                                
                                已有索引信息：
                                {indexes}
                                
                                业务背景：
                                {businessContext}
                                
                                待评估的优化结果：
                                
                                analysis:
                                {analysis}
                                
                                optimizedSql:
                                {optimizedSql}
                                
                                optimizationPoints:
                                {optimizationPoints}
                                
                                indexSuggestions:
                                {indexSuggestions}
                                
                                risk:
                                {risk}
                                """)
                        .param("prompt", EVALUATOR_PROMPT)
                        .param("sql", request.sql())
                        .param("schema", defaultString(request.schema()))
                        .param("indexes", defaultString(request.indexes()))
                        .param("businessContext", defaultString(request.businessContext()))
                        .param("analysis", generation.analysis())
                        .param("optimizedSql", generation.optimizedSql())
                        .param("optimizationPoints", generation.optimizationPoints())
                        .param("indexSuggestions", generation.indexSuggestions())
                        .param("risk", generation.risk()))
                .call()
                .entity(EvaluationResponse.class);
    }

    /**
     * 构造下一轮优化上下文。
     * @param memory             历史优化 SQL
     * @param evaluationResponse 最新评估反馈
     * @return 下一轮优化上下文
     */
    private String buildContext(List<String> memory,
                                EvaluationResponse evaluationResponse) {
        StringBuilder newContext = new StringBuilder();
        newContext.append("Previous attempts:");
        for (String sql : memory) {
            newContext.append("\n- ").append(sql);
        }
        newContext.append("\nFeedback: ")
                .append(evaluationResponse.feedback());
        return newContext.toString();
    }

    /**
     * 避免空值传入 Prompt。
     * @param value 字符串
     * @return 非空字符串
     */
    private String defaultString(String value) {
        return value == null ? "未提供" : value;
    }

    /**
     * Optimizer 生成结果。
     * @param analysis           SQL 问题分析
     * @param optimizedSql       优化后的 SQL
     * @param optimizationPoints 优化点说明
     * @param indexSuggestions   索引建议
     * @param risk               潜在风险
     */
    public record Generation(
            String analysis,
            String optimizedSql,
            String optimizationPoints,
            String indexSuggestions,
            String risk) {
    }

    /**
     * Evaluator 评估结果。
     * @param evaluation 评估结果
     * @param feedback   反馈意见
     */
    public record EvaluationResponse(
            Evaluation evaluation,
            String feedback) {
    }

    /**
     * 评估状态。
     */
    public enum Evaluation {
        PASS,
        NEEDS_IMPROVEMENT,
        FAIL
    }

    /**
     * 最终优化响应。
     * @param finalResult     最终优化结果
     * @param finalEvaluation 最终评估结果
     * @param chainOfThought  每一轮优化轨迹
     * @param passed          是否通过评估
     * @param rounds          实际优化轮次
     */
    public record RefinedResponse(
            Generation finalResult,
            EvaluationResponse finalEvaluation,
            List<Generation> chainOfThought,
            boolean passed,
            int rounds) {
    }
}
