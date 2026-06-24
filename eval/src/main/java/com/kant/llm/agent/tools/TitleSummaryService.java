package com.kant.llm.agent.tools;

import com.kant.ai.infra.client.*;
import com.kant.ai.infra.enums.ModelManufacturerEnum;
import com.kant.llm.eval.basesource.dao.entity.ModelInfoDO;
import com.kant.llm.eval.basesource.service.ModelInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 标题会话生成工具
 */
@Slf4j
@Component
public class TitleSummaryService {

    private final ChatClient chatClient;

    public TitleSummaryService() {
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
        return ChatClient.builder(
                        OpenAiChatModel.builder()
                                .openAiApi(OpenAiApi.builder()
                                        .apiKey("sk-3398a71a31f04728a19922dcbb8af1f9")
                                        .baseUrl("https://llm-3f6gkg8761t4jfw1.cn-beijing.maas.aliyuncs.com/compatible-mode")
                                        .build())
                                .defaultOptions(OpenAiChatOptions.builder()
                                        .model("qwen3.5-flash")
                                        .temperature(0.7)
                                        .extraBody(Map.of("enable_thinking", false))
                                        .build())
                                .build()
                )
                .build();
    }
}
