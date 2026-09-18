package com.kant.springai.agent.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.springai.agent.advisor.ConsumerBaseAdvisor;
import com.kant.springai.agent.advisor.ConsumerCallAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/advisor")
public class ModelClientAdvisorController {

    @PostMapping("/callAdvisor")
    public Result<String> callAdvisor() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey("QflVuNNAlVBZPwYKUfar:YCkTXhZTNkPfyrQJBaVf")
                        .baseUrl("https://spark-api-open.xf-yun.com/")
                        .build())
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model("Pro")
                                .build()
                )
                .build();
        ChatClient chatClient = ChatClient
                .builder(chatModel)
                .defaultAdvisors(new ConsumerBaseAdvisor(), new ConsumerCallAdvisor())
                .build();

        ChatClient.CallResponseSpec call = chatClient.prompt().user("你是什么大模型？").call();
        return Results.success(call.chatResponse().getResult().getOutput().getText());
    }
}
