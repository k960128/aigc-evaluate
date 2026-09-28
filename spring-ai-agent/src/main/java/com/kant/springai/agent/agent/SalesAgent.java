package com.kant.springai.agent.agent;

import com.kant.springai.agent.common.config.RedisChatMemoryRepository;
import com.kant.springai.agent.tools.*;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 销售数据分析助手
 */
@Slf4j
@Component
public class SalesAgent {

    private final AnomalyDetectionTool anomalyDetectionTool;
    private final ChartGeneratorTool chartGeneratorTool;
    private final SalesQueryTool salesQueryTool;
    private final SalesTrendTool salesTrendTool;
    private final SalesSummaryTool salesSummaryTool;
    private final RedisChatMemoryRepository redisChatMemoryRepository;
    private final ObservationRegistry observationRegistry;

    private final PromptTemplate SYSTEM_PROMPT_TEMPLATE = PromptTemplate.builder().template("""
            你是一个专业的销售数据分析助手，服务于销售团队。
            
            【当前时间】今天是 {today}。
            请严格基于此日期理解所有时间相关词语：
            - "今天/当前" = {today}
            - "本月" = {today} 所在的自然月（1日至月末）
            - "上个月" = {today} 所在月的上一个自然月
            - "本季度" = {today} 所在季度（Q1:1-3月, Q2:4-6月, Q3:7-9月, Q4:10-12月）
            - "今年" = {today} 所在年份的 1月1日 至 12月31日
            - "近N个月" = 从 {today} 往前推 N 个自然月
            
            你的能力：
            - 查询销售订单数据
            - 计算销售汇总统计（总额、排名、Top N）
            - 分析同比环比趋势
            - 生成图表数据（ECharts JSON 格式）
            - 检测销售数据异常
            
            你的限制（严格遵守）：
            - 只能查询数据，不能修改任何数据
            - 不能预测未来销售（没有预测能力）
            - 不能发送邮件、通知等操作
            - 如果问题超出能力范围，请明确告知并说明原因
            
            回答要求：
            - 用中文回答
            - 数据用具体数字，金额格式化为 ¥X,XXX
            - 有数据时给出简短的分析判断，不要只是罗列数据
            - 发现数据异常时主动提醒
            
            【图表输出规则 - 严格遵守】
            当工具返回的结果以 CHART_JSON: 开头时，你必须按如下格式输出：
            1. 先写一句简短的文字描述，例如：已为您生成近6个月销售趋势折线图：
            2. 紧接着在下一行原样输出工具返回的完整字符串（包含 CHART_JSON: 前缀和后面的 JSON），不得修改、截断、改写或省略。
            3. 不要用代码块（```）包裹，直接输出原始字符串。
            """).build();

    public SalesAgent(AnomalyDetectionTool anomalyDetectionTool,
                      ChartGeneratorTool chartGeneratorTool,
                      SalesQueryTool salesQueryTool,
                      SalesTrendTool salesTrendTool,
                      SalesSummaryTool salesSummaryTool,
                      RedisChatMemoryRepository redisChatMemoryRepository,
                      ObservationRegistry observationRegistry) {
        this.anomalyDetectionTool = anomalyDetectionTool;
        this.chartGeneratorTool = chartGeneratorTool;
        this.salesQueryTool = salesQueryTool;
        this.salesTrendTool = salesTrendTool;
        this.salesSummaryTool = salesSummaryTool;
        this.redisChatMemoryRepository = redisChatMemoryRepository;
        this.observationRegistry = observationRegistry;
    }

    /**
     * 与销售数据分析助手进行对话
     *
     * @param sessionId   会话ID
     * @param userMessage 用户消息
     * @param today       日期
     * @return 助手回复
     */
    public String chat(String sessionId, String userMessage, String today) {
        ChatClient chatClient = buildChatClient();
        return chatClient.prompt()
                .system(createSystemPrompt(today).getContents())
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call().chatResponse().getResult().getOutput().getText();
    }

    /**
     * 与销售数据分析助手进行对话，返回流式回复
     * @param sessionId   会话ID
     * @param userMessage 用户消息
     * @param today       日期
     * @return
     */
    public Flux<String> chatStream(String sessionId,
                                   String userMessage,
                                   String today) {

        ChatClient chatClient = buildChatClient();
        return chatClient.prompt()
                .system(createSystemPrompt(today).getContents())
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream().content();
    }



    private ChatClient buildChatClient() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey("sk-ws-H.EPMIPDE.c43E.MEUCIQDM5pWV1NMv_yHAkd5mfFVrcI5l3ArSIGpSFu9NALAL6AIgMeOyHGSWo6e3Fp4RwisiBhaa2bhOpZcdQ7HH4evlLEo")
                        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                        .build())
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model("qwen3.8-max")
                                .build()
                )
                .observationRegistry(observationRegistry)
                .build();
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(10)
                .chatMemoryRepository(redisChatMemoryRepository).build();

        return ChatClient
                .builder(chatModel, observationRegistry, null, null)
                .defaultToolCallbacks(ToolCallbacks.from(anomalyDetectionTool,
                        chartGeneratorTool,
                        salesQueryTool,
                        salesTrendTool,
                        salesSummaryTool))
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }


    private Prompt createSystemPrompt(String today) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("today", today);
        return SYSTEM_PROMPT_TEMPLATE.create(variables);
    }


}
