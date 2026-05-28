package com.kant.llm.rag;

import com.kant.llm.eval.client.*;
import com.kant.llm.eval.common.enums.ModelManufacturerEnum;
import com.kant.llm.eval.dao.entity.ModelInfoDO;
import com.kant.llm.eval.service.ModelInfoService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class RagQuestionRewriteTest {

    @Autowired
    private ModelInfoService modelInfoService;

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
                "3. 请直接输出JSON数组，不要包含解释或多余的文字。"+
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

        ModelInfoDO modelInfoDO = modelInfoService.getById(3L);

        PromptTemplate promptTemplate = new PromptTemplate(DECOMPOSE_PROMPT);
        promptTemplate.add("QUESTION", userQuestion);

        // 构建ChatModel
        ZhiPuAiChatModel zhiPuAiChatModel = new ZhiPuAiChatModel(ZhiPuAiApi.builder()
                .apiKey(modelInfoDO.getApiKey())
                .build(),
                ZhiPuAiChatOptions.builder()
                        .model(modelInfoDO.getModel())
                        .build());

        // 构建ChatClient
        ChatClient chatClient = ChatClient.builder(zhiPuAiChatModel).build();
        // 调用ChatClient
        String text = chatClient.prompt(promptTemplate.create()).call().chatResponse().getResult().getOutput().getText();
        System.out.println(text);
    }
}
