package com.kant.springai.agent.agent.multi;

import com.kant.springai.agent.common.construct.AgentContext;
import com.kant.springai.agent.tools.ResearchTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * 研究员 Agent：负责检索资料并输出结构化研究笔记。
 */
@Slf4j
@Component
public class ResearchAgent extends BaseAgent {

    public static final String KEY_NOTES = "research.notes";

    public ResearchAgent(ResearchTools tools) {
        super(buildChatModel(), """
                你是「研究员 Agent」，团队里负责信息收集的成员。
                工作方式：
                1. 先调用可用工具检索资料，可以针对不同角度多次调用；
                2. 汇总后输出一份结构化研究笔记。
                
                笔记格式（严格遵守）：
                ## 核心概念
                ## 关键事实
                ## 常见误区
                ## 可举例的场景
                
                只输出笔记本身，不要写完整文章，不要寒暄。
                """, tools);
    }

    @Override
    public String name() {
        return "ResearcherAgent";
    }

    @Override
    public String description() {
        return "检索并整理主题相关的资料，输出研究笔记";
    }

    @Override
    public void execute(AgentContext ctx) {
        String notes = chatClient.prompt()
                .user(u -> u.text("""
                        请围绕下面的主题展开调研：
                        
                        {topic}
                        """).param("topic", ctx.task()))
                .call()
                .content();

        ctx.put(KEY_NOTES, notes);
        ctx.log("[%s] 调研完成，产出 %d 字研究笔记"
                .formatted(name(), notes == null ? 0 : notes.length()));
    }

    private static ChatModel buildChatModel() {
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey("sk-ws-*****************************************")
                        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                        .build())
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model("qwen3.8-max")
                                .build()
                )
                .build();
    }
}
