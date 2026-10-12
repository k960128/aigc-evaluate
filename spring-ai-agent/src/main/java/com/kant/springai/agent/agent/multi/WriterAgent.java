package com.kant.springai.agent.agent.multi;

import com.kant.springai.agent.common.construct.AgentContext;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 写作 Agent：根据研究笔记撰写初稿，或根据审稿意见修订。
 */
@Component
public class WriterAgent extends BaseAgent {

    public static final String KEY_DRAFT = "writer.draft";

    public WriterAgent() {
        super(buildChatModel(), """
                你是「写作 Agent」，团队里负责成文的成员。
                写作要求：
                - Markdown 格式，包含一级标题、若干二级标题、必要的代码块、结尾总结；
                - 面向有基础的开发者，语言简洁，不堆砌形容词；
                - 只输出文章正文，不要解释你的写作过程，不要写"好的，我来写"之类的话。
                """);
    }

    @Override
    public String name() {
        return "WriterAgent";
    }

    @Override
    public String description() {
        return "根据研究笔记撰写文章初稿，并根据审稿意见修订";
    }

    @Override
    public void execute(AgentContext ctx) {
        String notes = ctx.get(ResearchAgent.KEY_NOTES);
        String previousDraft = ctx.get(KEY_DRAFT);
        List<String> issues = ctx.getOrDefault(ReviewerAgent.KEY_ISSUES, List.of());

        String userPrompt;
        if (previousDraft == null) {
            userPrompt = """
                    主题：%s
                    
                    研究笔记：
                    %s
                    
                    请撰写一篇完整的文章。
                    """.formatted(ctx.task(), notes);
        } else {
            userPrompt = """
                    主题：%s
                    
                    研究笔记：
                    %s
                    
                    上一版草稿：
                    %s
                    
                    审稿意见（请逐条修改，不要遗漏）：
                    %s
                    
                    请输出修改后的完整文章。
                    """.formatted(ctx.task(), notes, previousDraft,
                    issues.isEmpty() ? "（无）" : "- " + String.join("\n- ", issues));
        }

        String draft = chatClient.prompt().user(userPrompt).call().content();

        ctx.put(KEY_DRAFT, draft);
        ctx.log("[%s] 完成第 %d 稿，共 %d 字"
                .formatted(name(), ctx.round(), draft == null ? 0 : draft.length()));
    }

    private static ChatModel buildChatModel() {
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey("sk-ws-H.EPMIPDE.c43E.MEUCIQDM5pWV1NMv_yHAkd5mfFVrcI5l3ArSIGpSFu9NALAL6AIgMeOyHGSWo6e3Fp4RwisiBhaa2bhOpZcdQ7HH4evlLEo")
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