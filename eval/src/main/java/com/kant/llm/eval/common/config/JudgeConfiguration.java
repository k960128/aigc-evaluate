package com.kant.llm.eval.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 裁判大模型配置信息
 */
@Configuration
public class JudgeConfiguration {

    @Bean("judgeChatClient")
    public ChatClient buildJudgeChatClient() {

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                        .apiKey("sk-3398a71a31f04728a19922dcbb8af1f9")
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen-plus")
                        .build())
                .build();

        return ChatClient
                .builder(chatModel)
                .build();
    }
}
