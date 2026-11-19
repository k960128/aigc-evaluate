package com.kant.ai.infra.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 将统一模型请求转换为 Spring AI ChatClient 请求。
 *
 * <p>普通静态探测继续使用单条 inputText。多轮目标调用则使用已经由平台
 * 会话适配器校验过的消息列表，确保目标模型实际看到的是同一条会话轨迹。</p>
 */
public final class ChatClientRequestSupport {

    private ChatClientRequestSupport() {
    }

    /** 创建单轮或多轮 ChatClient 请求。 */
    public static ChatClient.ChatClientRequestSpec prompt(
            ChatClient client,
            ModelRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.getSystemMessages() != null) {
            messages.addAll(request.getSystemMessages());
        }
        if (request.getUserMessages() != null) {
            messages.addAll(request.getUserMessages());
        }
        if (messages.isEmpty()) {
            return client.prompt(request.getInputText());
        }
        if (request.getInputText() != null && !request.getInputText().isBlank()) {
            messages.add(new org.springframework.ai.chat.messages.UserMessage(request.getInputText()));
        }
        return client.prompt().messages(messages);
    }
}
