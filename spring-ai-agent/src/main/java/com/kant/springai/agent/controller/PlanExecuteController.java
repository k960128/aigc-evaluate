package com.kant.springai.agent.controller;

import com.kant.framework.convention.Result;
import com.kant.framework.web.Results;
import com.kant.springai.agent.agent.PlanExecuteAgent;
import com.kant.springai.agent.tools.SearchTool;
import com.kant.springai.agent.tools.WeatherQueryTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/plan-execute")
public class PlanExecuteController {

    @PostMapping("/execute")
       public Result<String> executePlan() {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
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
        // 构建PlanExecuteAgent智能体
        PlanExecuteAgent planExecuteAgent = PlanExecuteAgent.builder()
                .chatModel(chatModel)
                .chatMemory(memory)
                .tools(ToolCallbacks.from(new WeatherQueryTool(), new SearchTool()))
                .maxRounds(3)
                .maxToolRetries(2)
                .contextCharLimit(1000)
                .build();

        String result = planExecuteAgent.call("""
                请你先查询北京今天的天气，再搜索本周末北京天气的预警情况，并基于本周末北京的天气预警情况，搜索北京本周末适合旅游打卡的景点有哪些，最终生成一份不少于 500 字的综合天气分析报告。
                """);
        return Results.success("");
    }
}
