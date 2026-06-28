package com.kant.llm.agent.ai.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.stereotype.Component;

/**
 * 标题会话生成工具
 */
@Slf4j
@Component
public class TitleSummaryTool {

    private final ChatClient chatClient;

    public TitleSummaryTool() {
        this.chatClient = buildChatClient();
    }

    public String generateTitle(String userQuestion) {
        return chatClient.prompt()
                .system("你是一个对话标题生成助手。根据用户的第一句话，生成一个简洁的中文会话标题，要求：不超过20个字，不加引号，直接输出标题内容。")
                .user(u -> u.text("请根据以下用户问题生成会话标题：{question}").param("question", userQuestion))
                .call()
                .content();
    }

    private ChatClient buildChatClient() {
        ZhiPuAiChatModel chatModel = new ZhiPuAiChatModel(ZhiPuAiApi.builder()
                .apiKey("bae3e679181f4b659fe87ad698ff89d2.HlZWH3hYAesVxxwk")
                .build(),
                ZhiPuAiChatOptions.builder()
                        .model("GLM-4.7-Flash")
                        .temperature(0.7)
                        .thinking(ZhiPuAiApi.ChatCompletionRequest.Thinking.disabled()) // 关闭思考
                        .build());
        return ChatClient.builder(chatModel).build();
    }
}
