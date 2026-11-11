package com.kant.springai.agent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;

/**
 * 商户接入智能预审 Agent。
 * 这个类用于演示 Orchestrator-Workers（编排者 - 工作者模式）。
 * 这个案例模拟支付公司接入商户前的智能预审场景。
 */
@Slf4j
@Component
public class MerchantPreAuditAgent {
    private final ChatClient chatClient;

    public MerchantPreAuditAgent() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey("sk-ws-*****************************************")
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
                .build();
    }

    /**
     * Orchestrator Prompt。
     * 让总控 Agent 分析商户入网申请，并拆解成多个预审子任务。
     */
    private static final String ORCHESTRATOR_PROMPT = """
        你是支付公司的商户接入预审负责人。
        
        请分析下面这份商户入网申请资料，并拆解出 3～6 个需要 Worker 分别执行的预审任务。
        
        你需要重点判断：
        - 这个商户属于什么行业；
        - 是否可能涉及高风险行业；
        - 是否需要关注业务真实性；
        - 是否需要关注合规资质；
        - 是否需要关注交易和结算风险；
        - 是否需要关注技术接入复杂度；
        - 是否需要关注运营服务成本。
        
        商户入网申请：
        {application}
        
        请严格按照下面 JSON 格式返回，不要使用 Markdown 代码块，不要输出 JSON 之外的内容：
        
        \\{
          "analysis": "你对该商户入网申请的整体理解，以及为什么要拆解这些预审任务。",
          "tasks": [
            \\{
              "type": "business",
              "description": "从业务真实性和商业价值角度进行预审"
            \\},
            \\{
              "type": "risk",
              "description": "从欺诈、套现、退款、投诉、拒付等风险角度进行预审"
            \\}
          ]
        \\}
        """;

    /**
     * Worker Prompt。
     * 让具体 Worker 根据 Orchestrator 分配的子任务执行专业分析。
     */
    private static final String WORKER_PROMPT = """
            你是支付公司商户接入预审团队中的专业 Worker。
            
            请根据分配给你的任务，对商户入网申请进行专项预审。
            
            原始商户申请：
            {original_application}
            
            当前任务类型：
            {task_type}
            
            当前任务说明：
            {task_description}
            
            输出要求：
            - 只分析当前任务说明对应的角度；
            - 不要泛泛而谈；
            - 风险判断要具体；
            - 如果信息不足，要明确指出需要补充哪些材料；
            - 不要直接给出最终是否接入结论；
            - 输出 Markdown 格式。
            
            输出格式：
            
            ## 预审角度
            
            ## 主要发现
            
            ## 风险点
            
            ## 需要补充的材料
            
            ## 专项建议
            """;

    /**
     * 汇总 Prompt。
     * 将 Orchestrator 的任务分析和多个 Worker 的专项预审结果，
     * 汇总成最终商户接入预审报告。
     */
    private static final String SYNTHESIZER_PROMPT = """
            你是支付公司的商户接入预审负责人。
            
            下面是 Orchestrator 对商户入网申请的分析，以及多个 Worker 的专项预审结果。
            请你综合这些信息，生成一份最终的商户接入智能预审报告。
            
            要求：
            - 不要编造商户资料中不存在的信息；
            - 如果资料不足，要明确写出需要补充的材料；
            - 预审结论只能是“建议进入人工审核”“建议补充材料后再审核”“暂不建议接入”三种之一；
            - 风险等级只能是“低”“中”“高”三种之一；
            - 输出 Markdown 格式。
            
            最终报告格式：
            
            # 商户接入智能预审报告
            
            | 项目 | 结果 |
            |:--|:--|
            | 商户名称 | xxx |
            | 行业类型 | xxx |
            | 接入产品 | xxx |
            | 预计交易规模 | xxx |
            | 综合风险等级 | 低/中/高 |
            | 预审结论 | 建议进入人工审核/建议补充材料后再审核/暂不建议接入 |
            
            ## 主要风险点
            
            ## 专项预审摘要
            
            ## 需要补充的材料
            
            ## 人工复审建议
            
            原始商户申请：
            {original_application}
            
            Orchestrator 分析：
            {analysis}
            
            Worker 预审结果：
            {worker_responses}
            """;

    /**
     * 执行商户接入智能预审。
     * @param application 商户入网申请资料
     * @return 最终预审报告
     */
    public String process(String application) {
        FinalResponse finalResponse = processDetail(application);
        return finalResponse.finalReport();
    }

    /**
     * 执行商户接入智能预审，并返回完整过程结果。
     * @param application 商户入网申请资料
     * @return 包含 Orchestrator 分析、Worker 输出和最终报告的完整响应
     */
    public FinalResponse processDetail(String application) {
        Assert.hasText(application, "商户入网申请资料不能为空");

        // 1、Orchestrator 分析商户申请，并拆解预审任务。
        OrchestratorResponse orchestratorResponse = chatClient.prompt()
                .user(user -> user.text(ORCHESTRATOR_PROMPT)
                        .param("application", application))
                .call()
                .entity(OrchestratorResponse.class);

        log.info("""
                === ORCHESTRATOR OUTPUT ===
                ANALYSIS:
                {}
                
                TASKS:
                {}
                """, orchestratorResponse.analysis(), orchestratorResponse.tasks());

        // 2、多个 Worker 根据 Orchestrator 拆解出的任务分别执行专项预审。
        List<String> workerResponses = orchestratorResponse.tasks()
                .stream()
                .map(task -> chatClient.prompt()
                        .user(user -> user.text(WORKER_PROMPT)
                                .param("original_application", application)
                                .param("task_type", task.type())
                                .param("task_description", task.description()))
                        .call()
                        .content())
                .toList();

        log.info("""
                === WORKER OUTPUT ===
                {}
                """, workerResponses);

        // 3、汇总 Orchestrator 分析和多个 Worker 结果，生成最终预审报告。
        String finalReport = synthesize(application, orchestratorResponse, workerResponses);

        return new FinalResponse(
                orchestratorResponse.analysis(),
                orchestratorResponse.tasks(),
                workerResponses,
                finalReport
        );
    }

    /**
     * 汇总多个 Worker 的预审结果。
     * @param application 原始商户入网申请资料
     * @param orchestratorResponse Orchestrator 拆解结果
     * @param workerResponses Worker 专项预审结果
     * @return 最终商户接入智能预审报告
     */
    private String synthesize(String application,
                              OrchestratorResponse orchestratorResponse,
                              List<String> workerResponses) {
        // 将多个 Worker 的输出拼接成汇总上下文。
        StringBuilder context = new StringBuilder();

        for (int i = 0; i < workerResponses.size(); i++) {
            Task task = orchestratorResponse.tasks().get(i);

            context.append("## Worker ")
                    .append(i + 1)
                    .append("：")
                    .append(task.type())
                    .append("\n\n");

            context.append("### 任务说明\n")
                    .append(task.description())
                    .append("\n\n");

            context.append("### Worker 输出\n")
                    .append(workerResponses.get(i))
                    .append("\n\n");
        }

        // 调用大模型生成最终预审报告。
        return chatClient.prompt()
                .user(user -> user.text(SYNTHESIZER_PROMPT)
                        .param("original_application", application)
                        .param("analysis", orchestratorResponse.analysis())
                        .param("worker_responses", context.toString()))
                .call()
                .content();
    }

    /**
     * Orchestrator 拆解出的子任务。
     * @param type 任务类型，如 business、risk、compliance、technical、finance
     * @param description 任务说明，描述 Worker 要完成什么分析
     */
    public record Task(String type, String description) {
    }

    /**
     * Orchestrator 返回结果。
     * @param analysis Orchestrator 对原始任务的理解和拆解说明
     * @param tasks 拆解出的 Worker 子任务列表
     */
    public record OrchestratorResponse(String analysis, List<Task> tasks) {
    }

    /**
     * 最终响应结果。
     * @param analysis Orchestrator 分析
     * @param tasks Orchestrator 拆解出的任务
     * @param workerResponses 多个 Worker 的专项预审结果
     * @param finalReport 最终商户接入智能预审报告
     */
    public record FinalResponse(
            String analysis,
            List<Task> tasks,
            List<String> workerResponses,
            String finalReport) {
    }
}
