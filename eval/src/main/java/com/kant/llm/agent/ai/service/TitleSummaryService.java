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

    /**
     * 生成会话标题
     * TODO : 这里有一个问题，因为chatClient配置了会话记忆，如果这里不传的话，会默认放入一个default，这里优化单独配置一个chatClient，不用默认的
     *
     */
    public String generateTitle(String userQuestion) {
        return chatClient.prompt()
                .system("你是一个对话标题生成助手。根据用户的第一句话，生成一个简洁的中文会话标题，要求：不超过20个字，不加引号，直接输出标题内容。")
                .user(u -> u.text("请根据以下用户问题生成会话标题：{question}").param("question", userQuestion))
                .call()
                .content();
    }
}
