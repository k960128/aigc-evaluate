package com.kant.springai.agent.agent.multi;

import com.kant.springai.agent.common.construct.AgentContext;
import com.kant.springai.agent.dto.ReviewResult;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 审稿 Agent：对草稿打分并给出具体修改意见。
 * 使用 Spring AI 的结构化输出（entity）直接把模型输出映射为 Java record。
 */
@Component
public class ReviewerAgent extends BaseAgent {

    public static final String KEY_SCORE = "review.score";
    public static final String KEY_ISSUES = "review.issues";
    public static final String KEY_SUMMARY = "review.summary";

    public ReviewerAgent() {
        super(buildChatModel(), """
                你是「审稿 Agent」，团队里负责质量把关的成员。
                从准确性、结构、可读性、完整性四个维度评审文章。
                
                评分标准：
                - 90 分以上：可直接发布
                - 80-89 分：小修即可
                - 80 分以下：需要实质性修改
                
                只列出真正需要修改的问题，最多 5 条，每条一句话，要具体可执行。
                """);
    }

    @Override
    public String name() {
        return "ReviewerAgent";
    }

    @Override
    public String description() {
        return "评审文章，给出分数与修改意见";
    }

    @Override
    public void execute(AgentContext ctx) {
        String draft = ctx.get(WriterAgent.KEY_DRAFT);

        ReviewResult result = chatClient.prompt()
                .user("""
                        请评审以下文章。
                        
                        主题：%s
                        
                        文章：
                        %s
                        """.formatted(ctx.task(), draft))
                .call()
                .entity(ReviewResult.class);

        if (result == null) {
            result = new ReviewResult(70, List.of("模型未返回结构化评审结果"), "评审失败，默认不通过");
        }
        List<String> issues = result.issues() == null ? List.of() : result.issues();

        ctx.put(KEY_SCORE, result.score());
        ctx.put(KEY_ISSUES, issues);
        ctx.put(KEY_SUMMARY, result.summary());
        ctx.log("[%s] 第 %d 轮评审得分 %d，问题 %d 条"
                .formatted(name(), ctx.round(), result.score(), issues.size()));
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