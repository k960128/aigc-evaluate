package com.kant.agent.scope.examples;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;

public class MultiTurnChatExamples {
    private static final String apiKey = "sk-ws-*****************************************";

    public static void main(String[] args) {
        // 创建 Memory（负责维护会话历史）
        InMemoryMemory memory = new InMemoryMemory();

        // 创建 Agent
        ReActAgent agent = ReActAgent.builder()
                .name("Assistant")
                .sysPrompt("你是一个乐于助人的人工智能助手。记住用户告诉你的事情。")
                .model(DashScopeChatModel.builder()
                        .apiKey(apiKey)
                        .modelName("qwen-max")
                        .build())
                .memory(memory)   // 注入 Memory
                .build();

        // === 第1轮 ===
        Msg msg1 = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text("我的姓名是 Hollis，我是一个软件工程师。").build())
                .build();
        Msg reply1 = agent.call(msg1).block();
        System.out.println("Agent: " + reply1.getTextContent());

        // === 第2轮（Agent 能记住第1轮信息）===
        Msg msg2 = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text("我的姓名和我做什么？").build())
                .build();
        Msg reply2 = agent.call(msg2).block();
        System.out.println("Agent: " + reply2.getTextContent());
        // Agent 会回答: "Your name is Hollis and you're a software engineer."

        // 查看 Memory 中的完整对话历史
        System.out.println("内存中的消息总数: " + memory.getMessages().size());
        // 输出: 4（user1 + assistant1 + user2 + assistant2）
    }
}
