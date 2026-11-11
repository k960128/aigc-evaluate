package com.kant.springai.agent.agent;

import com.kant.springai.agent.common.config.RedisChatMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * 实现 HR 智能筛选评估简历的 Prompt Chaining 工作流模式。
 * 该案例模拟真实 HR 简历筛选场景，将一份候选人简历逐步加工成结构化筛选报告。
 */
@Slf4j
@Component
public class ResumeEvaluationAgent {

    private final RedisChatMemoryRepository redisChatMemoryRepository;

    public ResumeEvaluationAgent(RedisChatMemoryRepository redisChatMemoryRepository) {
        this.redisChatMemoryRepository = redisChatMemoryRepository;
    }


    /**
     * 工作流中的每一步 Prompt，当前步骤输出，会作为下一步输入。
     */
    private static final String[] DEFAULT_SYSTEM_PROMPTS = {

            // 第一步
            """
                请从简历中提取候选人的核心信息。
                
                仅输出信息以下信息：
                - 年龄
                - 学历
                - 工作年限
                - 技术栈
                - 项目经验
                - 当前岗位
                - 目标岗位
                """,

            // 第二步
            """
                根据上一步提取的信息，分析候选人的技术能力与项目质量。
                
                重点分析：
                - 是否离职超过三个月、最近工作的一家公司是否超过一年（可提取最后一份工作的截止时间进行计算，如显示至今或者为空表示在职）
                - 技术深度
                - 技术广度
                - 项目复杂度
                - 是否具备高并发/分布式经验
                - 是否有 AI 相关落地能力
                - 是否存在明显简历包装
                
                输出格式：
                - 技术能力：
                - 项目质量：
                - 风险点：
                - 综合评价：
                """,

            // 第三步
            """
                根据前面的技术分析结果，评估候选人与目标岗位的匹配度，如果无目标岗位，默认为 Java 后端开发工程师。
                
                匹配等级：
                - 高
                - 中
                - 低
                
                需要分析：
                - 离职时长
                - 最近一家公司的工作时长
                - 技能匹配情况
                - 工作经验匹配情况
                - 是否满足岗位要求
                - 是否建议进入下一轮
                
                输出格式：
                - 匹配等级：
                - 匹配分析：
                - 是否推荐面试：
                - 推荐原因：
                """,

            // 第四步
            """
                根据前面的所有分析结果，生成最终 HR 筛选报告表格。
                
                要求：
                - 只输出一张 Markdown 表格；
                - 不要输出表格之外的任何标题、说明、总结、列表或补充段落；
                - 表格内容使用简洁专业的 HR 风格；
                - 核心优势、主要风险点和最终筛选结论都写入表格对应单元格；
                
                输出格式必须严格如下：
                | 项目 | 结果 |
                |:--|:--|
                | 候选人 | xxx |
                | 年龄 | xxx |
                | 学历 | xxx |
                | 工作年限 | xxx |
                | 在职情况 | xxx |
                | 技术能力 | xxx |
                | 项目质量 | xxx |
                | 匹配等级 | xxx |
                | 风险点 | xxx |
                | 是否推荐面试 | xxx |
                | 筛选结论 | xxx |
                """};

    /**
     * 执行串行工作流。
     * 每一步都会处理上一步的输出结果。
     * 属于 Chain Workflow（串行工作流）模式。
     * @param resumeContent 候选人简历内容
     * @return 最终 HR 筛选报告
     */
    public String evaluation(String resumeContent) {
        int step = 0;
        String conversationId = "ResumeEvaluationAgent_" + System.currentTimeMillis();
        String response = resumeContent;

        // 串行执行工作流
        for (String prompt : DEFAULT_SYSTEM_PROMPTS) {

            // 1、组合当前步骤输入
            String input = String.format("""
                    【当前任务】
                    %s
                    
                    【上一步结果】
                    %s
                    """, prompt, response);

            // 2、调用大模型处理当前步骤
            response = sparkChatClient().prompt(input)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call().content();

            // 3、输出当前步骤结果
            log.info("\nSTEP {}:\n{}", step++, response);
        }

        return response;
    }


    private ChatClient sparkChatClient() {
        MessageWindowChatMemory chatMemory  = MessageWindowChatMemory.builder().maxMessages(10)
                .chatMemoryRepository(redisChatMemoryRepository).build();
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
        return ChatClient
                .builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
