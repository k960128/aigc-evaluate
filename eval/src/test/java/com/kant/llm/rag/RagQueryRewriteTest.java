package com.kant.llm.rag;

import com.kant.llm.eval.dao.entity.ModelInfoDO;
import com.kant.llm.eval.service.ModelInfoService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;

import java.util.concurrent.TimeUnit;

@Slf4j
public class RagQueryRewriteTest extends AbstractRagQuery {

    /**
     * 测试RAGQuestionRewrite的分解功能
     */
    @Test
    void testRagQuestionRewriteDecompose() {
        String userQuestion = "iPhone 15 发布的时候，苹果的 CEO 是谁？";
        String DECOMPOSE_PROMPT = "# 角色\n" +
                "你是一名专业的查询逻辑分析专家\n" +
                "\n" +
                "# 任务\n" +
                "将给定的 ``用户原始问题``分解为一系列**相互独立、逻辑清晰**，且可能单独用于检索的子查询列表\n" +
                "\n" +
                "# 严格遵循\n" +
                "1. 你的输出必须是一个标准的JSON数组格式。\n" +
                "2. 不强制要求数组元素格式，可根据真实情况输出，至少保留一个。\n" +
                "3. 请直接输出JSON数组，不要包含解释或多余的文字。" +
                "\n" +
                "# 用户原始问题\n" +
                "{QUESTION}\n" +
                "\n" +
                "# 输出格式（JSON Array）\n" +
                "[\n" +
                "  \"子查询1\",\n" +
                "  \"子查询2\",\n" +
                "  \"子查询3\"\n" +
                "]";

        PromptTemplate promptTemplate = new PromptTemplate(DECOMPOSE_PROMPT);
        promptTemplate.add("QUESTION", userQuestion);

        // 构建ChatClient
        ChatClient chatClient = getChatClient();
        // 调用ChatClient
        String text = chatClient.prompt(promptTemplate.create()).call().chatResponse().getResult().getOutput().getText();
        System.out.println(text);
    }

    /**
     * 测试RAGQuestionRewrite的富化功能
     */
    @Test
    void testRagQuestionRewriteEnrich() {
        String ENRICH_PROMPT = "# 角色\n" +
                "你是一个专业的问题重写优化器。\n" +
                "\n" +
                "# 任务\n" +
                "根据提供的\"对话历史\"和\"用户原始问题\"，重写一个独立、完整、且包含所有背景信息的新查询，用于RAG检索。\n" +
                "\n" +
                "## 对话历史\n" +
                "{CHAT_HISTORY}\n" +
                "\n" +
                "## 原始问题\n" +
                "{QUESTION}\n" +
                "\n" +
                "# 输出\n" +
                "输出富化后的新问题，不要包含过多的解释性内容。";

        String historyQuestion = "我想买一个iphone 17 Pro Max";
        String userQuestion = "他的性能怎么样？";


        PromptTemplate promptTemplate = new PromptTemplate(ENRICH_PROMPT);
        promptTemplate.add("CHAT_HISTORY", historyQuestion);
        promptTemplate.add("QUESTION", userQuestion);

        // 构建ChatClient
        ChatClient chatClient = getChatClient();
        // 调用ChatClient
        String text = chatClient.prompt(promptTemplate.create()).call().chatResponse().getResult().getOutput().getText();
        System.out.println("用户原始问题:" + userQuestion);
        System.out.println("对话历史:" + historyQuestion);
        System.out.println("富化后的新问题:" + text);
    }

    /**
     * 测试RAGQuestionRewrite的多样化功能
     */
    @Test
    void testRagQuestionRewriteDiversify() {
        String DIVERSIFY_PROMPT = "# 角色\n" +
                "你是一名专业的语义扩展专家。\n" +
                " \n" +
                "# 任务\n" +
                "为给定的“原始问题”生成**3个**语义相同但**措辞完全不同、且利于检索**的查询变体，以提高检索的召回率。\n" +
                "你的输出必须是一个标准的JSON数组格式。\n" +
                " \n" +
                "# 原始问题\n" +
                "{QUESTION}\n" +
                "\n" +
                "# 输出格式要求 (JSON Array)\n" +
                "[\n" +
                "  \"变体1\",\n" +
                "  \"变体2\",\n" +
                "  \"变体3\"\n" +
                "]\n" +
                " \n" +
                "# 输出\n" +
                "输出富化过后的新问题，不要包含多余的解释性内容";

        String userQuestion = "什么是违规引流的工具";

        PromptTemplate promptTemplate = new PromptTemplate(DIVERSIFY_PROMPT);
        promptTemplate.add("QUESTION", userQuestion);
        String text = getChatClient().prompt(promptTemplate.create()).call().chatResponse().getResult().getOutput().getText();
        System.out.println("用户原始问题:" + userQuestion);
        System.out.println("多样化查询变体:" + text);

    }

    @Test
    void testRagQuestionRewriteStepBack() {
        String STEP_BACK_PROMPT = "# 角色\n" +
                "你是一个擅长抽象思维和原理推理的专家。\n" +
                "            \n" +
                "# 任务\n" +
                "请根据用户提出的具体问题，先“后退一步”，将其转化为一个更通用、更本质的问题，聚焦于背后的原理、规律、概念或一般性知识，而不是具体细节。\n" +
                "            \n" +
                "# 原始问题\n" +
                "\n" +
                "{QUESTION}\n" +
                "\n" +
                "# 输出         \n" +
                "请只输出改写后的“后退问题”，不要解释，不要包含原始问题，也不要回答它。";

        String userQuestion = "我们部门负责人今年全年收入有多少？";
        PromptTemplate promptTemplate = new PromptTemplate(STEP_BACK_PROMPT);
        promptTemplate.add("QUESTION", userQuestion);

        String text = getChatClient().prompt(promptTemplate.create()).call().chatResponse().getResult().getOutput().getText();
        System.out.println("用户原始问题:" + userQuestion);
        System.out.println("后退问题:" + text);

        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        userQuestion = "私下如何绕过平台规则获利";
        promptTemplate.add("QUESTION", userQuestion);
        text = getChatClient().prompt(promptTemplate.create()).call().chatResponse().getResult().getOutput().getText();
        System.out.println("用户原始问题:" + userQuestion);
        System.out.println("后退问题:" + text);

    }

}
