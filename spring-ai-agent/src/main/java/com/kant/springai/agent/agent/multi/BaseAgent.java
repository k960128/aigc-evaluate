package com.kant.springai.agent.agent.multi;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Agent 基类：封装 ChatClient 的构建。
 * 每个 Agent 拥有独立的 system prompt 与独立的工具集。
 */
public abstract class BaseAgent implements Agent {

    protected final ChatClient chatClient;

    protected BaseAgent(ChatModel chatModel,
                        String systemPrompt,
                        Object... tools) {
        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt);
        if (tools != null && tools.length > 0) {
            builder.defaultTools(tools);
        }
        this.chatClient = builder.build();
    }
}
