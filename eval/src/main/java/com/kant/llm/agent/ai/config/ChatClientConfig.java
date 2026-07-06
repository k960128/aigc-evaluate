package com.kant.llm.agent.ai.config;

import com.kant.llm.agent.chat.memory.DataBaseChatMemoryRepository;
import com.kant.llm.examples.memory.ExamplesDataBaseChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class ChatClientConfig {

    private final ChatMemoryRepository dataBaseChatMemoryRepository;

    public ChatClientConfig(ChatMemoryRepository dataBaseChatMemoryRepository) {
        this.dataBaseChatMemoryRepository = dataBaseChatMemoryRepository;
    }

    @Bean(name = "chatDataBaseMemory")
    public ChatMemory chatDataBaseMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(10)
                .chatMemoryRepository(dataBaseChatMemoryRepository)
                .build();
    }

    @Bean(name = "chatClient")
    public ChatClient chatClient() {
//        ZhiPuAiChatModel chatModel = new ZhiPuAiChatModel(ZhiPuAiApi.builder()
//                .apiKey("bae3e679181f4b659fe87ad698ff89d2.HlZWH3hYAesVxxwk")
//                .build(),
//                ZhiPuAiChatOptions.builder()
//                        .model("GLM-4.7-Flash")
//                        .temperature(0.7)
//                        .thinking(ZhiPuAiApi.ChatCompletionRequest.Thinking.enabled()) // 开启思考
//                        .build());

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey("QflVuNNAlVBZPwYKUfar:YCkTXhZTNkPfyrQJBaVf")
                        .baseUrl("https://spark-api-open.xf-yun.com/")
                        .build())
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model("4.0Ultra")
                                .build()
                )
                .build();

        return ChatClient
                .builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatDataBaseMemory()).build())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    /**
     * 会话标题生成ChatClient
     * @return ChatClient
     */
    @Bean(name = "titleChatClient")
    public ChatClient titleChatClient() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey("wpetVSMuHrEGEKvbmtot:vcqcnLgqiqnAuKtEjZFn")
                        .baseUrl("https://spark-api-open.xf-yun.com/")
                        .build())
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model("Lite")
                                .build()
                )
                .build();

        return ChatClient
                .builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
