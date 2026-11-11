package com.kant.llm.examples.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.llm.examples.dto.ChatStatus;
import com.kant.llm.examples.dto.OrderChat;
import com.kant.llm.examples.prompt.ExamplesPromptTemplate;
import com.kant.llm.examples.tools.OrderTools;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * 退款接口
 */
@Slf4j
@RestController
@RequestMapping("/examples/order")
public class OrderRefundController {

    private final OrderTools orderTools;
    private final ChatMemory examplesChatMemory;
    private ChatClient chatClient;
    private final ChatMemory examplesDataBaseChatMemory;

    public OrderRefundController(OrderTools orderTools,
                                 ChatMemory examplesChatMemory,
                                 @Qualifier("examplesDataBaseChatMemory") ChatMemory examplesDataBaseChatMemory) {
        this.orderTools = orderTools;
        this.examplesChatMemory = examplesChatMemory;
        this.examplesDataBaseChatMemory = examplesDataBaseChatMemory;
    }


    @GetMapping("/newChat")
    public Result<OrderChat> newChat(String userId, String orderId, HttpServletResponse httpServletResponse) {
        httpServletResponse.setCharacterEncoding("UTF-8");

        //模拟数据库创建一个chat的记录，获取到他的唯一id。
        String chatId = UUID.randomUUID().toString();
        OrderChat orderChat = chatClient
                .prompt()
                .user(String.format("我要咨询订单相关的售后问题，我的用户id是%s,我的订单号是: %s ,本地的对话Id是 %s，当前状态是 %s", userId, orderId, chatId, ChatStatus.CHAT_START.name()))
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId)
                        .param("chat_memory_retrieve_size", 100))
                .call().entity(OrderChat.class);

        return Results.success(orderChat);
    }

    @GetMapping("/ask")
    public Result<String> ask(String question, String chatId, HttpServletResponse httpServletResponse) {
        httpServletResponse.setCharacterEncoding("UTF-8");

        return Results.success(chatClient
                .prompt()
                .user(question)
                .tools(orderTools)
                .advisors(spec -> spec.param(CONVERSATION_ID, chatId)
                        .param("chat_memory_retrieve_size", 100)).call().content());
    }

    @PostConstruct
    public void init() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey("sk-ws-*****************************************")
                        .baseUrl("https://llm-3f6gkg8761t4jfw1.cn-beijing.maas.aliyuncs.com/compatible-mode")
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen3.5-flash")
                        .temperature(0.7)
                        .extraBody(Map.of("enable_thinking", false))
                        .build())
                .build();

        chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(examplesDataBaseChatMemory).build())
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultSystem(ExamplesPromptTemplate.REFUND_PROMPT)
                .build();
    }
}
