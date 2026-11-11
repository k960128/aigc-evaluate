package com.kant.llm.examples.controller;

import com.kant.llm.examples.dto.SingerSongs;
import com.kant.llm.examples.prompt.ExamplesPromptTemplate;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/examples/structured")
@RequiredArgsConstructor
public class ExamplesStructuredController {

     private ChatClient chatClient;

     @GetMapping("/singerSongs")
     public Object singerSongs(@RequestParam(value = "singer") String singer) {
          return chatClient.prompt()
                  .user(u -> u.text("生成歌手 {singer} 的 5 首原创歌曲名称。")
                          .param("singer", singer))
                  .call()
                  .entity(SingerSongs.class);
     }

     @PostConstruct
     public void init() {
//          OpenAiChatModel chatModel = OpenAiChatModel.builder()
//                  .openAiApi(OpenAiApi.builder()
//                          .apiKey("sk-ws-*****************************************")
//                          .baseUrl("https://llm-3f6gkg8761t4jfw1.cn-beijing.maas.aliyuncs.com/compatible-mode")
//                          .build())
//                  .defaultOptions(OpenAiChatOptions.builder()
//                          .model("qwen3.5-flash")
//                          .temperature(0.7)
//                          .extraBody(Map.of("enable_thinking", false))
//                          .build())
//                  .build();
//
//          chatClient = ChatClient.builder(chatModel)
//                  .defaultAdvisors(new SimpleLoggerAdvisor())
//                  .build();

          ZhiPuAiChatModel chatModel = new ZhiPuAiChatModel(ZhiPuAiApi.builder()
                  .apiKey("sk-ws-*****************************************")
                  .build(),
                  ZhiPuAiChatOptions.builder()
                          .model("GLM-4.7-Flash")
                          .temperature(0.7)
                          .build());
             chatClient = ChatClient
                  .builder(chatModel)
                  .defaultAdvisors(new SimpleLoggerAdvisor())
                  .build();
     }
}
