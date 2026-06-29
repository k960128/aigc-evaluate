package com.kant.llm.agent.ai.service;

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
public class TitleSummaryService {

    private final ChatClient chatClient;

    public TitleSummaryService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String generateTitle(String userQuestion) {
        return chatClient.prompt()
                .system("你是一个对话标题生成助手。根据用户的第一句话，生成一个简洁的中文会话标题，要求：不超过20个字，不加引号，直接输出标题内容。")
                .user(u -> u.text("请根据以下用户问题生成会话标题：{question}").param("question", userQuestion))
                .call()
                .content();
    }
}
