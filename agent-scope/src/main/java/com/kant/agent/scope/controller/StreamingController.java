package com.kant.agent.scope.controller;

import com.alibaba.fastjson2.JSON;
import com.kant.agent.scope.tools.SimpleTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/stream")
public class StreamingController {

    private final String apiKey = "sk-ws-H.EPMIPDE.c43E.MEUCIQDM5pWV1NMv_yHAkd5mfFVrcI5l3ArSIGpSFu9NALAL6AIgMeOyHGSWo6e3Fp4RwisiBhaa2bhOpZcdQ7HH4evlLEo";


    @GetMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestParam String message, HttpServletResponse httpServletResponse) {
        httpServletResponse.setCharacterEncoding("UTF-8");

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools());

        // 创建 Agent，注意 model 需要 stream(true)
        ReActAgent agent = ReActAgent.builder()
                .name("WebAgent").toolkit(toolkit)
                .model(DashScopeChatModel.builder()
                        .apiKey(apiKey)
                        .modelName("qwen-plus")
                        .stream(true)  // 开启模型级流式
                        .build())
                .build();

        // 构建用户消息
        Msg userMsg = Msg.builder().textContent(message).build();

        // 配置流式选项 — 增量模式
        StreamOptions streamOptions = StreamOptions.builder()
                // 选择要接收的事件类型
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT, EventType.AGENT_RESULT)
                // true = 增量模式（只发送新增内容），false = 累积模式（每次发送全部已累积内容）
                .incremental(true)
                // 是否包含最终推理结果（把流式输出的内容拼在一起一次性返回）
                .includeReasoningResult(false)
                .build();


//        return agent.stream(userMsg, streamOptions)
//                .subscribeOn(Schedulers.boundedElastic())
//                .map(event -> event.getMessage().getTextContent())
//                .filter(text -> text != null && !text.isEmpty());

        // 调用 stream() 获取事件流
        return agent.stream(userMsg, streamOptions)
                .subscribeOn(Schedulers.boundedElastic())
                .map(event -> JSON.toJSONString(event.getMessage()))
                .filter(text -> text != null && !text.isEmpty());
    }
}

