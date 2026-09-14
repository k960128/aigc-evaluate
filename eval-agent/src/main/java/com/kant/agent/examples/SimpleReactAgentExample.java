package com.kant.agent.examples;

import com.kant.agent.examples.tools.SearchTool;
import com.kant.agent.examples.tools.WeatherQueryTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

@Slf4j
public class SimpleReactAgentExample {
    static void main() {
        String question = """
                请你根据北京今天的天气、未来七天的天气趋势、以及上海今天的天气，并搜索北京天气的预警情况，生成一份不少于 600 字的综合分析报告。
                """;

        SimpleReactAgent simpleReactAgent = build();
        String call = simpleReactAgent.call(question);
        log.info("call: {}", call);
    }

    private static SimpleReactAgent build() {
        String name = "SimpleReactAgent";
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(20).build();
        int maxRounds = 5;
        ToolCallback[] toolCallbacks = ToolCallbacks.from(new WeatherQueryTool(), new SearchTool());
        ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey("sk-ws-H.EPMIPDE.c43E.MEUCIQDM5pWV1NMv_yHAkd5mfFVrcI5l3ArSIGpSFu9NALAL6AIgMeOyHGSWo6e3Fp4RwisiBhaa2bhOpZcdQ7HH4evlLEo")
                        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                        .build())
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .model("qwen3.8-max")
                                .build()
                )
                .build();

        String systemPrompt = "你是专业的研究分析助手！";
        return new SimpleReactAgent(
                chatModel, List.of(toolCallbacks), maxRounds, systemPrompt
        );
    }
}
